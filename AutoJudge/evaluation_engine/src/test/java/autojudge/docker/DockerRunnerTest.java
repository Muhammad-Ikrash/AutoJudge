package autojudge.docker;

import autojudge.config.ContainerConfig;
import autojudge.config.DockerConstants;
import autojudge.model.ExecutionResult;
import autojudge.model.Submission;
import autojudge.model.TestCase;
import autojudge.model.Verdict;
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

    @Test
    void runsSimplePythonSubmission() throws Exception {
        Path submissionDirectory = tempDir.resolve("submission");
        Files.createDirectories(submissionDirectory);
        Files.writeString(
            submissionDirectory.resolve("solution.py"),
            "import sys\nprint(sys.stdin.read().strip())\n",
            java.nio.charset.StandardCharsets.UTF_8
        );

        Path inputFile = tempDir.resolve("input.txt");
        Files.writeString(inputFile, "hello\n", java.nio.charset.StandardCharsets.UTF_8);
        Path expectedOutput = tempDir.resolve("expected.txt");
        Files.writeString(expectedOutput, "hello\n", java.nio.charset.StandardCharsets.UTF_8);

        Submission submission = new Submission(
            submissionDirectory,
            inputFile,
            expectedOutput,
            "student-1",
            "assignment-1"
        );

        TestCase testCase = new TestCase("case-1", inputFile, expectedOutput, 1);
        ContainerConfig config = new ContainerConfig(DockerConstants.DEFAULT_IMAGE, 256, 1.0, "bridge", "/workspace", true);

        DockerRunner runner = new DockerRunner();
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
            "import sys\nwith open('exploit.bin', 'w') as f:\n    f.write('malicious')\nprint(sys.stdin.read().strip())\n",
            java.nio.charset.StandardCharsets.UTF_8
        );

        Path inputFile = tempDir.resolve("input_malicious.txt");
        Files.writeString(inputFile, "hello\n", java.nio.charset.StandardCharsets.UTF_8);
        Path expectedOutput = tempDir.resolve("expected_malicious.txt");
        Files.writeString(expectedOutput, "hello\n", java.nio.charset.StandardCharsets.UTF_8);

        Submission submission = new Submission(
            submissionDirectory,
            inputFile,
            expectedOutput,
            "student-malicious",
            "assignment-1"
        );

        TestCase testCase = new TestCase("case-1", inputFile, expectedOutput, 1);
        ContainerConfig config = new ContainerConfig(DockerConstants.DEFAULT_IMAGE, 256, 1.0, "bridge", "/workspace", true);

        DockerRunner runner = new DockerRunner();
        List<ExecutionResult> results = runner.runSubmission(config, submission, List.of(testCase));

        assertEquals(1, results.size());
        ExecutionResult result = results.get(0);
        assertEquals(Verdict.MALICIOUS_CODE, result.verdict());
    }
}
