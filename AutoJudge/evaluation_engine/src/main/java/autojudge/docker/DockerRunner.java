package autojudge.docker;

import autojudge.compiler.CompileCommandBuilder;
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
        List<ExecutionResult> results = new ArrayList<>();
        String containerId = null;

        try {
            // 1. Container created first with configs and started
            containerId = containerManager.createInstance(config);
            containerManager.startInstance(containerId);

            Path targetContainerPath = Path.of(config.workingDirectory());

            // Check docker copy convention: does target folder already exist inside container?
            boolean targetAlreadyExists = containerManager.directoryExists(containerId, targetContainerPath.toString());

            // 2. Copy submission files into container
            containerManager.copyToContainer(containerId, submission.getSubmissionRoot(), targetContainerPath);

            String submissionFolderName = submission.getSubmissionRoot().getFileName() != null
                    ? submission.getSubmissionRoot().getFileName().toString()
                    : "";

            // If target folder already existed, data is copied into target/submissionFolderName
            // If target folder did not exist, data is copied directly inside target folder created on spot
            String containerSubmissionDir = targetAlreadyExists && !submissionFolderName.isEmpty()
                    ? targetContainerPath.resolve(submissionFolderName).toString()
                    : targetContainerPath.toString();

            // 3. Scan submission and compile within container
            SubmissionLayout layout = SubmissionScanner.scan(submission.getSubmissionRoot());
            List<String> rawCompileCommand = CompileCommandBuilder.buildCommand(layout);

            if (!rawCompileCommand.isEmpty()) {
                // Wrap compilation command with timeout (30 seconds limit)
                String compileScript = "timeout -k 1s 30s " + String.join(" ", rawCompileCommand);
                ExecCMD compileResult = containerManager.execInDir(containerId, List.of("sh", "-c", compileScript), containerSubmissionDir);
                if (compileResult.getExitCode() != 0) {
                    Verdict compileVerdict = (compileResult.getExitCode() == 124 || compileResult.getExitCode() == 137)
                            ? Verdict.TIME_LIMIT_EXCEEDED
                            : Verdict.COMPILATION_ERROR;

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
            }

            // 4. Testcase execution loop
            long timeLimitSec = config.timeLimitSeconds();

            for (TestCase testCase : testCases) {
                if (testCase == null) continue;

                String testCaseInputFileName = testCase.inputFile().getFileName() != null
                        ? testCase.inputFile().getFileName().toString()
                        : "input.in";
                String testCaseContainerPath = containerSubmissionDir + "/" + testCaseInputFileName;
                containerManager.copyToContainer(containerId, testCase.inputFile(), Path.of(containerSubmissionDir));

                // Snapshot files copied / existing before execution
                List<String> filesBeforeExecList = containerManager.listFiles(containerId, containerSubmissionDir);
                Set<String> filesBeforeExec = new HashSet<>(filesBeforeExecList);

                long startTime = System.currentTimeMillis();

                // Build runner script wrapped with per-test-case timeout and SIGKILL escalation (-k 1s)
                String rawRunnerScript = resolveExecutionCommand(layout, testCaseInputFileName);
                String runnerScript = "timeout -k 1s " + timeLimitSec + "s " + rawRunnerScript;

                List<String> execCmd = List.of("sh", "-c", runnerScript);
                ExecCMD execResult = containerManager.execInDir(containerId, execCmd, containerSubmissionDir);
                long executionTime = System.currentTimeMillis() - startTime;

                // Malicious code check: snapshot files after execution
                List<String> filesAfterExecList = containerManager.listFiles(containerId, containerSubmissionDir);
                Set<String> filesAfterExec = new HashSet<>(filesAfterExecList);
                filesAfterExec.removeAll(filesBeforeExec);

                if (!filesAfterExec.isEmpty()) {
                    // Code created unauthorized files -> Malicious Code!
                    // Destroy container immediately and edit verdict
                    containerManager.kill(containerId);
                    containerManager.removeContainer(containerId);
                    containerId = null;

                    results.add(new ExecutionResult(
                            testCase.id(),
                            Verdict.MALICIOUS_CODE,
                            "",
                            "",
                            "Malicious code detected: created unauthorized file(s) " + filesAfterExec,
                            -1,
                            executionTime,
                            0
                    ));

                    int currentIdx = testCases.indexOf(testCase);
                    for (int i = currentIdx + 1; i < testCases.size(); i++) {
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
                    break;
                }

                // Check output size capping
                String stdout = execResult.getStdout() != null ? execResult.getStdout() : "";
                String stderr = execResult.getStderr() != null ? execResult.getStderr() : "";
                boolean outputExceeded = stdout.length() > MAX_OUTPUT_LENGTH || stderr.length() > MAX_OUTPUT_LENGTH;

                if (outputExceeded) {
                    if (stdout.length() > MAX_OUTPUT_LENGTH) stdout = stdout.substring(0, MAX_OUTPUT_LENGTH) + "\n[Output truncated]";
                    if (stderr.length() > MAX_OUTPUT_LENGTH) stderr = stderr.substring(0, MAX_OUTPUT_LENGTH) + "\n[Output truncated]";
                }

                // Determine verdict from exit code and container inspect (OOM)
                Verdict verdict;
                int exitCode = execResult.getExitCode();

                if (outputExceeded) {
                    verdict = Verdict.RUNTIME_ERROR;
                    stderr = stderr + "\nOutput limit exceeded";
                } else if (exitCode == 0) {
                    verdict = Verdict.ACCEPTED;
                } else if (exitCode == 124 || exitCode == 137) {
                    if (containerManager.isOOMKilled(containerId)) {
                        verdict = Verdict.MEMORY_LIMIT_EXCEEDED;
                    } else {
                        verdict = Verdict.TIME_LIMIT_EXCEEDED;
                    }
                } else {
                    if (containerManager.isOOMKilled(containerId)) {
                        verdict = Verdict.MEMORY_LIMIT_EXCEEDED;
                    } else {
                        verdict = Verdict.RUNTIME_ERROR;
                    }
                }

                results.add(new ExecutionResult(
                        testCase.id(),
                        verdict,
                        stdout,
                        "",
                        stderr,
                        exitCode,
                        executionTime,
                        0
                ));

                // Clean up testcase input file so only compiled and submission files remain
                containerManager.removeFile(containerId, testCaseContainerPath);
            }

        } catch (Exception e) {
            if (results.isEmpty()) {
                for (TestCase testCase : testCases) {
                    if (testCase != null) {
                        results.add(new ExecutionResult(
                                testCase.id(),
                                Verdict.INTERNAL_ERROR,
                                "",
                                "",
                                e.getMessage(),
                                -1,
                                0,
                                0
                        ));
                    }
                }
            }
        } finally {
            if (containerId != null) {
                containerManager.stopContainer(containerId);
                containerManager.removeContainer(containerId);
            }
        }

        return results;
    }

    private String resolveExecutionCommand(SubmissionLayout layout, String inputFileName) {
        return switch (layout.language()) {
            case CPP, C -> "./solution < " + inputFileName;
            case PYTHON -> {
                String mainScript = layout.sourceFiles().isEmpty() ? "main.py" : layout.sourceFiles().get(0).toString();
                yield "python3 " + mainScript + " < " + inputFileName;
            }
            case JAVA -> {
                String mainClass = layout.sourceFiles().isEmpty() ? "Main" : layout.sourceFiles().get(0).toString().replace(".java", "");
                yield "java " + mainClass + " < " + inputFileName;
            }
        };
    }
}

