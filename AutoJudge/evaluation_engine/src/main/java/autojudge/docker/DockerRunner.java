package autojudge.docker;

import autojudge.compiler.CompileCommandBuilder;
import autojudge.compiler.ExecutionCommandBuilder;
import autojudge.compiler.SubmissionLayout;
import autojudge.compiler.SubmissionScanner;
import autojudge.model.ExecCMD;
import autojudge.model.ExecutionResult;
import autojudge.model.Submission;
import autojudge.model.TestCase;
import autojudge.model.Verdict;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class DockerRunner {

    private static final int MAX_OUTPUT_LENGTH = 1_000_000;
    private final ContainerManager containerManager;

    public DockerRunner() {
        this(new ContainerManager(DockerClientFactory.getClient()));
    }

    public DockerRunner(ContainerManager containerManager) {
        this.containerManager = Objects.requireNonNull(containerManager, "containerManager");
    }

    public List<ExecutionResult> runSubmission(
            ContainerConfig config,
            Submission submission,
            List<TestCase> testCases
    ) {
        String containerId = null;
        try {
            log("Step 1: Provisioning & starting Docker container for student: " + submission.studentId());
            containerId = createAndStartContainer(config);
            log("Container started successfully. ID: " + containerId);

            log("Step 2: Copying submission source files into container");
            String submissionDir = copySubmissionToContainer(containerId, config, submission);
            log("Submission copied to container path: " + submissionDir);

            log("Step 3: Scanning layout and compiling submission inside container");
            SubmissionLayout layout = SubmissionScanner.scan(submission.getSubmissionRoot());
            ExecCMD compileResult = compileSubmission(containerId, submissionDir, layout);
            if (isCompileFailed(compileResult)) {
                log("Compilation failed with exit code " + compileResult.getExitCode());
                return buildCompilationErrorResults(testCases, compileResult);
            }
            log("Compilation succeeded");

            log("Step 4: Executing " + testCases.size() + " test cases");
            return executeAllTestCases(containerId, submissionDir, layout, testCases, config.timeLimitSeconds());

        } catch (Exception e) {
            logError("Execution failed due to exception: " + e.getMessage(), e);
            return buildInternalErrorResults(testCases, e);
        } finally {
            log("Step 5: Cleaning up container resources");
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

    private ExecCMD compileSubmission(
            String containerId,
            String submissionDir,
            SubmissionLayout layout
    ) throws Exception {
        List<String> rawCompileCommand = CompileCommandBuilder.buildCommand(layout);
        if (rawCompileCommand.isEmpty()) {
            return new ExecCMD(0, "", "");
        }

        String compileScript = "timeout -k 1s 30s " + String.join(" ", rawCompileCommand);
        return containerManager.execInDir(containerId, List.of("sh", "-c", compileScript), submissionDir);
    }

    private boolean isCompileFailed(ExecCMD compileResult) {
        return compileResult != null && compileResult.getExitCode() != 0;
    }

    private List<ExecutionResult> buildCompilationErrorResults(
            List<TestCase> testCases,
            ExecCMD compileResult
    ) {
        Verdict compileVerdict = (compileResult.getExitCode() == 124 || compileResult.getExitCode() == 137)
                ? Verdict.TIME_LIMIT_EXCEEDED
                : Verdict.COMPILATION_ERROR;

        List<ExecutionResult> results = new ArrayList<>();
        for (TestCase testCase : testCases) {
            results.add(new ExecutionResult(
                    testCase.id(),
                    compileVerdict,
                    "",
                    "",
                    compileResult.getStderr(),
                    compileResult.getExitCode(),
                    0,
                    0
            ));
        }
        return results;
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

            log("Executing Test Case: " + testCase.id());
            ExecutionResult result = executeSingleTestCase(containerId, submissionDir, layout, testCase, timeLimitSec);
            log("Test Case " + testCase.id() + " finished -> Verdict: " + result.verdict() + " [ExitCode: " + result.exitCode() + ", Time: " + result.executionTime() + "ms]");

            results.add(result);

            if (result.verdict() == Verdict.MALICIOUS_CODE) {
                log("Malicious code detected! Destroying container immediately.");
                destroyContainer(containerId);
                appendMaliciousCodeAbortionResults(results, testCases, i + 1);
                break;
            }
        }

        return results;
    }

    private ExecutionResult executeSingleTestCase(
            String containerId,
            String submissionDir,
            SubmissionLayout layout,
            TestCase testCase,
            long timeLimitSec
    ) throws Exception {
        String inputFileName = resolveInputFileName(testCase);
        String testCaseContainerPath = submissionDir + "/" + inputFileName;

        containerManager.copyToContainer(containerId, testCase.inputFile(), Path.of(submissionDir));

        Set<String> filesBeforeExec = new HashSet<>(containerManager.listFiles(containerId, submissionDir));
        long startTime = System.currentTimeMillis();

        String runnerScript = "timeout -k 1s " + timeLimitSec + "s " + ExecutionCommandBuilder.buildExecutionCommand(layout, inputFileName);
        ExecCMD execResult = containerManager.execInDir(
                containerId, List.of("sh", "-c", runnerScript), submissionDir, timeLimitSec
        );
        long executionTime = System.currentTimeMillis() - startTime;

        Set<String> filesAfterExec = new HashSet<>(containerManager.listFiles(containerId, submissionDir));
        Set<String> unauthorizedFiles = detectUnauthorizedFiles(filesBeforeExec, filesAfterExec);

        if (!unauthorizedFiles.isEmpty()) {
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

        containerManager.removeFile(containerId, testCaseContainerPath);
        return evaluateExecutionResult(containerId, testCase, execResult, executionTime);
    }

    private String resolveInputFileName(TestCase testCase) {
        return (testCase.inputFile() != null && testCase.inputFile().getFileName() != null)
                ? testCase.inputFile().getFileName().toString()
                : "input.in";
    }

    private Set<String> detectUnauthorizedFiles(Set<String> filesBefore, Set<String> filesAfter) {
        Set<String> createdFiles = new HashSet<>(filesAfter);
        createdFiles.removeAll(filesBefore);
        return createdFiles.stream()
                .filter(f -> !isAllowedOrBenignFile(f))
                .collect(Collectors.toSet());
    }

    private boolean isAllowedOrBenignFile(String filePath) {
        if (filePath == null || filePath.isBlank()) return true;
        String lower = filePath.toLowerCase();

        if (lower.contains("__pycache__") || lower.endsWith(".pyc") || lower.endsWith(".pyo")) {
            return true;
        }
        if (lower.endsWith(".class") || lower.contains("hs_err_pid")) {
            return true;
        }
        if (lower.endsWith(".tmp") || lower.endsWith(".log") || lower.endsWith(".out") || lower.endsWith(".txt")) {
            return true;
        }
        return false;
    }

    private ExecutionResult evaluateExecutionResult(
            String containerId,
            TestCase testCase,
            ExecCMD execResult,
            long executionTime
    ) {
        String stdout = execResult.getStdout() != null ? execResult.getStdout() : "";
        String stderr = execResult.getStderr() != null ? execResult.getStderr() : "";
        boolean outputExceeded = execResult.isTruncated() || stdout.length() > MAX_OUTPUT_LENGTH || stderr.length() > MAX_OUTPUT_LENGTH;

        if (outputExceeded) {
            if (stdout.length() > MAX_OUTPUT_LENGTH) stdout = stdout.substring(0, MAX_OUTPUT_LENGTH) + "\n[Output truncated]";
            if (stderr.length() > MAX_OUTPUT_LENGTH) stderr = stderr.substring(0, MAX_OUTPUT_LENGTH) + "\n[Output truncated]";
        }

        Verdict verdict = determineVerdict(containerId, execResult.getExitCode(), outputExceeded);
        if (outputExceeded) {
            stderr = stderr + "\nOutput limit exceeded";
        }

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

    private Verdict determineVerdict(String containerId, int exitCode, boolean outputExceeded) {
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

    private void appendMaliciousCodeAbortionResults(
            List<ExecutionResult> results,
            List<TestCase> testCases,
            int startIndex
    ) {
        for (int i = startIndex; i < testCases.size(); i++) {
            TestCase remaining = testCases.get(i);
            if (remaining != null) {
                results.add(new ExecutionResult(
                        remaining.id(),
                        Verdict.MALICIOUS_CODE,
                        "",
                        "",
                        "Execution aborted due to malicious code",
                        -1,
                        0,
                        0
                ));
            }
        }
    }

    private List<ExecutionResult> buildInternalErrorResults(List<TestCase> testCases, Exception e) {
        List<ExecutionResult> results = new ArrayList<>();
        for (TestCase testCase : testCases) {
            if (testCase != null) {
                results.add(new ExecutionResult(
                        testCase.id(),
                        Verdict.INTERNAL_ERROR,
                        "",
                        "",
                        e.getMessage() != null ? e.getMessage() : e.getClass().getName(),
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
            containerManager.stopContainer(containerId);
            containerManager.removeContainer(containerId);
        }
    }

    private static void log(String message) {
        StackTraceElement frame = Thread.currentThread().getStackTrace()[2];
        System.out.println("[" + frame.getFileName() + ":" + frame.getLineNumber() + "] " + message);
    }

    private static void logError(String message, Throwable throwable) {
        StackTraceElement frame = Thread.currentThread().getStackTrace()[2];
        System.err.println("[" + frame.getFileName() + ":" + frame.getLineNumber() + "] [ERROR] " + message);
        if (throwable != null) {
            throwable.printStackTrace(System.err);
        }
    }
}
