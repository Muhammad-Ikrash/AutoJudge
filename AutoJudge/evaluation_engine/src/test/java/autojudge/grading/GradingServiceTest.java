package autojudge.grading;

import autojudge.model.ExecutionResult;
import autojudge.model.Submission;
import autojudge.model.SubmissionResult;
import autojudge.model.TestCase;
import autojudge.model.Verdict;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradingServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void gradesAcceptedSubmissionWithWeightedScore() throws Exception {
        GradingService gradingService = new GradingService(new OutputComparator(), new ScoreCalculator());

        Path expectedOne = tempDir.resolve("expected1.txt");
        Path expectedTwo = tempDir.resolve("expected2.txt");
        Files.writeString(expectedOne, "7\n");
        Files.writeString(expectedTwo, "14\n");

        Submission submission = new Submission(
            Path.of("student.cpp"),
            Path.of("input1.txt"),
            Path.of("expected1.txt"),
            "student-1",
            "assignment-1"
        );

        TestCase easyCase = new TestCase("case-1", Path.of("input1.txt"), expectedOne, 1);
        TestCase hardCase = new TestCase("case-2", Path.of("input2.txt"), expectedTwo, 3);

        ExecutionResult acceptedResult = new ExecutionResult("case-1", Verdict.ACCEPTED, "7\n", "7\n", "", 0, 1, 1);
        ExecutionResult acceptedResultTwo = new ExecutionResult("case-2", Verdict.ACCEPTED, "14\n", "14\n", "", 0, 1, 1);

        SubmissionResult result = gradingService.grade(
            submission,
            List.of(easyCase, hardCase),
            List.of(acceptedResult, acceptedResultTwo)
        );

        assertEquals(100.0d, result.score());
        assertEquals(Verdict.ACCEPTED, result.verdict());
        assertEquals(2, result.passedTests());
        assertEquals(2, result.totalTests());
    }

    @Test
    void treatsMissingExpectedOutputAsInternalError() {
        GradingService gradingService = new GradingService(new OutputComparator(), new ScoreCalculator());

        Submission submission = new Submission(
            Path.of("student.cpp"),
            Path.of("input1.txt"),
            Path.of("missing-output.txt"),
            "student-2",
            "assignment-1"
        );

        TestCase testCase = new TestCase("case-1", Path.of("input1.txt"), Path.of("missing-output.txt"), 1);
        ExecutionResult executionResult = new ExecutionResult("case-1", Verdict.ACCEPTED, "7\n", "", "", 0, 1, 1);

        SubmissionResult result = gradingService.grade(
            submission,
            List.of(testCase),
            List.of(executionResult)
        );

        assertEquals(0.0d, result.score());
        assertEquals(Verdict.INTERNAL_ERROR, result.verdict());
        assertEquals(0, result.passedTests());
        assertEquals(1, result.totalTests());
    }

    @Test
    void resolvesSubmissionIdFromFileName() throws Exception {
        GradingService gradingService = new GradingService(new OutputComparator(), new ScoreCalculator());

        Path expectedOutput = tempDir.resolve("expected1.txt");
        Files.writeString(expectedOutput, "7\n");

        Submission submission = new Submission(
            Path.of("/tmp/solution.cpp"),
            Path.of("input1.txt"),
            expectedOutput,
            "student-3",
            "assignment-1"
        );

        TestCase testCase = new TestCase("case-1", Path.of("input1.txt"), expectedOutput, 1);
        ExecutionResult executionResult = new ExecutionResult("case-1", Verdict.ACCEPTED, "7\n", "7\n", "", 0, 1, 1);

        SubmissionResult result = gradingService.grade(
            submission,
            List.of(testCase),
            List.of(executionResult)
        );

        assertEquals("solution.cpp", result.submissionId());
        assertTrue(result.score() > 0.0d);
    }
}
