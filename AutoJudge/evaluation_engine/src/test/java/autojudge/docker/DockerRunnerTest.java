package autojudge.docker;

import autojudge.CoreEvaluation.config.ContainerConfig;
import autojudge.CoreEvaluation.config.DockerConstants;
import autojudge.CoreEvaluation.docker.DockerRunner;
import autojudge.CoreEvaluation.model.ExecCMD;
import autojudge.CoreEvaluation.model.ExecutionResult;
import autojudge.CoreEvaluation.model.Submission;
import autojudge.CoreEvaluation.model.TestCase;
import autojudge.CoreEvaluation.model.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerRunnerTest {

    @TempDir
    Path tempDir;

    private MockContainerManager mockManager;
    private DockerRunner runner;

    @BeforeEach
    void setUp() {
        mockManager = new MockContainerManager();
        runner = new DockerRunner(mockManager);
    }

    @Test
    void runsSimplePythonSubmission() throws Exception {
        Path submissionDirectory = tempDir.resolve("submission");
        Files.createDirectories(submissionDirectory);
        Files.writeString(
            submissionDirectory.resolve("solution.py"),
            "import sys\nprint(sys.stdin.read().strip())\n"
        );

        Path inputFile = tempDir.resolve("input.txt");
        Files.writeString(inputFile, "hello\n");
        Path expectedOutput = tempDir.resolve("expected.txt");
        Files.writeString(expectedOutput, "hello\n");

        Submission submission = new Submission(
            submissionDirectory,
            inputFile,
            expectedOutput,
            "student-1",
            "assignment-1"
        );

        TestCase testCase = new TestCase("case-1", inputFile, expectedOutput, 1);
        ContainerConfig config = new ContainerConfig(DockerConstants.DEFAULT_IMAGE, 256, 1.0, "bridge", "/workspace", true);

        mockManager.setExecResponse(new ExecCMD(0, "hello", ""));

        List<ExecutionResult> results = runner.runSubmission(config, submission, List.of(testCase));

        assertEquals(1, results.size());
        ExecutionResult result = results.get(0);
        assertEquals(Verdict.ACCEPTED, result.verdict());
        assertEquals("hello", result.studentOutput().trim());
        assertEquals(0, result.exitCode());
        assertTrue(result.executionTime() >= 0);
    }

    @Test
    void detectsMaliciousCodeAndDestroysContainer() throws Exception {
        Path submissionDirectory = tempDir.resolve("malicious_submission");
        Files.createDirectories(submissionDirectory);
        Files.writeString(
            submissionDirectory.resolve("solution.py"),
            "import sys\nwith open('exploit.bin', 'w') as f:\n    f.write('malicious')\nprint(sys.stdin.read().strip())\n"
        );

        Path inputFile = tempDir.resolve("input_malicious.txt");
        Files.writeString(inputFile, "hello\n");
        Path expectedOutput = tempDir.resolve("expected_malicious.txt");
        Files.writeString(expectedOutput, "hello\n");

        Submission submission = new Submission(
            submissionDirectory,
            inputFile,
            expectedOutput,
            "student-malicious",
            "assignment-1"
        );

        TestCase testCase = new TestCase("case-1", inputFile, expectedOutput, 1);
        ContainerConfig config = new ContainerConfig(DockerConstants.DEFAULT_IMAGE, 256, 1.0, "bridge", "/workspace", true);

        mockManager.enqueueFilesResponse(List.of("/workspace/solution.py"));
        mockManager.enqueueFilesResponse(List.of("/workspace/solution.py", "/workspace/exploit.bin"));

        List<ExecutionResult> results = runner.runSubmission(config, submission, List.of(testCase));

        assertEquals(1, results.size());
        ExecutionResult result = results.get(0);
        assertEquals(Verdict.MALICIOUS_CODE, result.verdict());
        assertTrue(mockManager.isDestroyed());
    }

    @Test
    void handlesCompilationFailure() throws Exception {
        Path submissionDirectory = tempDir.resolve("cpp_submission");
        Files.createDirectories(submissionDirectory);
        Files.writeString(
            submissionDirectory.resolve("main.cpp"),
            "int main() { return error; }"
        );

        Path inputFile = tempDir.resolve("input.txt");
        Files.writeString(inputFile, "hello\n");
        Path expectedOutput = tempDir.resolve("expected.txt");
        Files.writeString(expectedOutput, "hello\n");

        Submission submission = new Submission(
            submissionDirectory,
            inputFile,
            expectedOutput,
            "student-cpp",
            "assignment-1"
        );

        TestCase testCase = new TestCase("case-1", inputFile, expectedOutput, 1);
        ContainerConfig config = new ContainerConfig(DockerConstants.DEFAULT_IMAGE, 256, 1.0, "bridge", "/workspace", true);

        mockManager.setExecResponse(new ExecCMD(1, "", "main.cpp:1: error: 'error' was not declared in this scope"));

        List<ExecutionResult> results = runner.runSubmission(config, submission, List.of(testCase));

        assertEquals(1, results.size());
        assertEquals(Verdict.COMPILATION_ERROR, results.get(0).verdict());
    }
}
