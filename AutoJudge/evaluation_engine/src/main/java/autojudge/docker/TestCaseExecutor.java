package autojudge.docker;

import autojudge.compiler.ExecutionCommandBuilder;
import autojudge.config.DockerConstants;
import autojudge.exception.DockerException;
import autojudge.model.ExecCMD;
import autojudge.model.ExecutionResult;
import autojudge.model.SubmissionLayout;
import autojudge.model.TestCase;
import autojudge.model.Verdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Handles test case setup, execution, malicious code checks, output validation, and verdict determination.
 */
public class TestCaseExecutor {

    private static final Logger log = LoggerFactory.getLogger(TestCaseExecutor.class);

    private final ContainerManager containerManager;
    private final MaliciousCodeDetector maliciousCodeDetector;
    private final OutputValidator outputValidator;

    public TestCaseExecutor(ContainerManager containerManager) {
        this(containerManager, new MaliciousCodeDetector(), new OutputValidator());
    }

    public TestCaseExecutor(
            ContainerManager containerManager,
            MaliciousCodeDetector maliciousCodeDetector,
            OutputValidator outputValidator
    ) {
        this.containerManager = Objects.requireNonNull(containerManager, "containerManager must not be null");
        this.maliciousCodeDetector = Objects.requireNonNull(maliciousCodeDetector, "maliciousCodeDetector must not be null");
        this.outputValidator = Objects.requireNonNull(outputValidator, "outputValidator must not be null");
    }

    public ExecutionResult executeSingleTestCase(
            String containerId,
            String submissionDir,
            SubmissionLayout layout,
            TestCase testCase,
            long timeLimitSec
    ) throws DockerException {
        String inputFileName = resolveInputFileName(testCase);
        String testCaseContainerPath = submissionDir + "/" + inputFileName;

        if (testCase.inputFile() != null) {
            containerManager.copyToContainer(containerId, testCase.inputFile(), Path.of(submissionDir));
        }

        Set<String> filesBeforeExec = new HashSet<>(containerManager.listFiles(containerId, submissionDir));
        long startTime = System.currentTimeMillis();

        String runnerScript = "timeout -k " + DockerConstants.SIGKILL_GRACE_PERIOD + " "
                + timeLimitSec + "s "
                + ExecutionCommandBuilder.buildExecutionCommand(layout, inputFileName);

        ExecCMD execResult = containerManager.execInDir(
                containerId, List.of("sh", "-c", runnerScript), submissionDir, timeLimitSec
        );
        long executionTime = System.currentTimeMillis() - startTime;

        Set<String> filesAfterExec = new HashSet<>(containerManager.listFiles(containerId, submissionDir));
        Set<String> unauthorizedFiles = maliciousCodeDetector.detectUnauthorizedFiles(filesBeforeExec, filesAfterExec);

        if (!unauthorizedFiles.isEmpty()) {
            cleanupTestcaseFile(containerId, testCaseContainerPath);
            return new ExecutionResult(
                    testCase.id(),
                    Verdict.MALICIOUS_CODE,
                    "",
                    "",
                    "Malicious code detected: created unauthorized file(s) " + unauthorizedFiles,
                    -1,
                    executionTime,
                    0
            );
        }

        cleanupTestcaseFile(containerId, testCaseContainerPath);
        return evaluateExecutionResult(containerId, testCase, execResult, executionTime);
    }

    private String resolveInputFileName(TestCase testCase) {
        return (testCase.inputFile() != null && testCase.inputFile().getFileName() != null)
                ? testCase.inputFile().getFileName().toString()
                : "input.in";
    }

    private ExecutionResult evaluateExecutionResult(
            String containerId,
            TestCase testCase,
            ExecCMD execResult,
            long executionTime
    ) {
        String rawStdout = execResult.getStdout() != null ? execResult.getStdout() : "";
        String rawStderr = execResult.getStderr() != null ? execResult.getStderr() : "";

        boolean outputExceeded = outputValidator.isOutputExceeded(execResult);
        String stdout = outputValidator.truncateIfNeeded(rawStdout);
        String stderr = outputValidator.processStderr(rawStderr, outputExceeded);

        Verdict verdict = determineVerdict(containerId, execResult.getExitCode(), outputExceeded);

        return new ExecutionResult(
                testCase.id(),
                verdict,
                stdout,
                "",
                stderr,
                execResult.getExitCode(),
                executionTime,
                0
        );
    }

    public Verdict determineVerdict(String containerId, int exitCode, boolean outputExceeded) {
        if (outputExceeded) {
            return Verdict.RUNTIME_ERROR;
        }
        if (exitCode == 0) {
            return Verdict.ACCEPTED;
        }

        boolean isOOM = containerManager.isOOMKilled(containerId);
        if (exitCode == 124 || exitCode == 137) {
            return isOOM ? Verdict.MEMORY_LIMIT_EXCEEDED : Verdict.TIME_LIMIT_EXCEEDED;
        }
        return isOOM ? Verdict.MEMORY_LIMIT_EXCEEDED : Verdict.RUNTIME_ERROR;
    }

    private void cleanupTestcaseFile(String containerId, String testCaseContainerPath) {
        containerManager.removeFile(containerId, testCaseContainerPath);
    }
}
