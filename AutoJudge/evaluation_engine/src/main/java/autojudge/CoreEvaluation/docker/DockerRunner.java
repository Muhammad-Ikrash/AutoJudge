package autojudge.CoreEvaluation.docker;

import autojudge.CoreEvaluation.compiler.SubmissionScanner;
import autojudge.CoreEvaluation.config.ContainerConfig;
import autojudge.CoreEvaluation.model.ExecCMD;
import autojudge.CoreEvaluation.model.ExecutionResult;
import autojudge.CoreEvaluation.model.Submission;
import autojudge.CoreEvaluation.model.SubmissionLayout;
import autojudge.CoreEvaluation.model.TestCase;
import autojudge.CoreEvaluation.model.Verdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates container lifecycle and submission evaluation.
 */
public final class DockerRunner {

    private static final Logger log = LoggerFactory.getLogger(DockerRunner.class);

    private final ContainerManager containerManager;
    private final CompilationOrchestrator compilationOrchestrator;
    private final TestCaseExecutor testCaseExecutor;

    public DockerRunner() {
        this(new DefaultContainerManager(DockerClientFactory.getClient()));
    }

    public DockerRunner(ContainerManager containerManager) {
        this(
            containerManager,
            new CompilationOrchestrator(containerManager),
            new TestCaseExecutor(containerManager)
        );
    }

    public DockerRunner(
            ContainerManager containerManager,
            CompilationOrchestrator compilationOrchestrator,
            TestCaseExecutor testCaseExecutor
    ) {
        this.containerManager = Objects.requireNonNull(containerManager, "containerManager must not be null");
        this.compilationOrchestrator = Objects.requireNonNull(compilationOrchestrator, "compilationOrchestrator must not be null");
        this.testCaseExecutor = Objects.requireNonNull(testCaseExecutor, "testCaseExecutor must not be null");
    }

    public List<ExecutionResult> runSubmission(
            ContainerConfig config,
            Submission submission,
            List<TestCase> testCases
    ) {
        String containerId = null;
        try {
            SubmissionLayout layout = SubmissionScanner.scan(submission.getSubmissionRoot());

            containerId = createAndStartContainer(config);

            String submissionDir = copySubmissionToContainer(containerId, config, submission);

            ExecCMD compileResult = compilationOrchestrator.compile(containerId, submissionDir, layout);
            if (compilationOrchestrator.isCompileFailed(compileResult)) {
                Verdict compileVerdict = (compileResult.getExitCode() == 124 || compileResult.getExitCode() == 137)
                        ? Verdict.TIME_LIMIT_EXCEEDED
                        : Verdict.COMPILATION_ERROR;
                return buildErrorResults(testCases, compileVerdict, compileResult.getStderr(), 0);
            }

            return executeAllTestCases(containerId, submissionDir, layout, testCases, config.timeLimitSeconds());

        } catch (Exception e) {
            log.error("Internal error during submission evaluation for student {}", submission.studentId(), e);
            return buildErrorResults(testCases, Verdict.INTERNAL_ERROR, e.getMessage() != null ? e.getMessage() : e.getClass().getName(), 0);
        } finally {
            destroyContainer(containerId);
        }
    }

    private String createAndStartContainer(ContainerConfig config) throws Exception {
        String containerId = containerManager.createInstance(config);
        containerManager.startInstance(containerId);
        return containerId;
    }

    private String copySubmissionToContainer(
            String containerId,
            ContainerConfig config,
            Submission submission
    ) throws Exception {
        Path targetContainerPath = Path.of(config.workingDirectory());
        boolean targetAlreadyExists = containerManager.directoryExists(containerId, targetContainerPath.toString());

        containerManager.copyToContainer(containerId, submission.getSubmissionRoot(), targetContainerPath);

        String submissionFolderName = submission.getSubmissionRoot().getFileName() != null
                ? submission.getSubmissionRoot().getFileName().toString()
                : "";

        return (targetAlreadyExists && !submissionFolderName.isEmpty())
                ? targetContainerPath.resolve(submissionFolderName).toString()
                : targetContainerPath.toString();
    }

    private List<ExecutionResult> executeAllTestCases(
            String containerId,
            String submissionDir,
            SubmissionLayout layout,
            List<TestCase> testCases,
            long timeLimitSec
    ) throws Exception {
        List<ExecutionResult> results = new ArrayList<>();

        for (int i = 0; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            if (testCase == null) continue;

            ExecutionResult result = testCaseExecutor.executeSingleTestCase(
                    containerId, submissionDir, layout, testCase, timeLimitSec
            );

            log.info("[TestCase: {}] | Time: {} ms", testCase.id(), result.executionTime());

            results.add(result);

            if (result.verdict() == Verdict.MALICIOUS_CODE || result.verdict() == Verdict.PROCESS_LIMIT_EXCEEDED) {
                log.warn("Aborting remaining test cases due to malicious code in test case {}", testCase.id());
                destroyContainer(containerId);
                results.addAll(buildErrorResults(testCases, result.verdict(), "Execution aborted due to " + result.verdict().name(), i + 1));
                break;
            }
        }

        return results;
    }

    private List<ExecutionResult> buildErrorResults(
            List<TestCase> testCases,
            Verdict verdict,
            String errorMessage,
            int startIndex
    ) {
        List<ExecutionResult> results = new ArrayList<>();
        for (int i = startIndex; i < testCases.size(); i++) {
            TestCase testCase = testCases.get(i);
            if (testCase != null) {
                results.add(new ExecutionResult(
                        testCase.id(),
                        verdict,
                        "",
                        "",
                        errorMessage,
                        -1,
                        0,
                        0
                ));
            }
        }
        return results;
    }

    private void destroyContainer(String containerId) {
        if (containerId != null) {
            try {
                containerManager.stopContainer(containerId);
                containerManager.removeContainer(containerId);
            } catch (Exception e) {
                log.warn("Error destroying container {}: {}", containerId, e.getMessage());
            }
        }
    }
}
