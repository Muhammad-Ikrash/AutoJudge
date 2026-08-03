package autojudge.grading;

import autojudge.model.ExecutionResult;
import autojudge.model.Submission;
import autojudge.model.SubmissionResult;
import autojudge.model.TestCase;
import autojudge.model.Verdict;
import autojudge.util.FileUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GradingService {

    private final OutputComparator outputComparator;
    private final ScoreCalculator scoreCalculator;

    public GradingService() {
        this(new OutputComparator(), new ScoreCalculator());
    }

    GradingService(OutputComparator outputComparator, ScoreCalculator scoreCalculator) {
        this.outputComparator = outputComparator;
        this.scoreCalculator = scoreCalculator;
    }

    public SubmissionResult grade(
        Submission submission,
        List<TestCase> testCases,
        List<ExecutionResult> executionResults
    ) {
        Map<String, TestCase> testCaseById = new HashMap<>();
        for (TestCase testCase : testCases) {
            if (testCase != null && testCase.id() != null) {
                testCaseById.putIfAbsent(testCase.id(), testCase);
            }
        }

        int passedTests = 0;
        int earnedWeight = 0;
        int totalWeight = 0;
        Verdict finalVerdict = Verdict.ACCEPTED;

        for (TestCase testCase : testCases) {
            if (testCase != null) {
                totalWeight += testCase.weight();
            }
        }

        for (ExecutionResult executionResult : executionResults) {
            TestCase testCase = testCaseById.get(executionResult.testCaseId());
            if (testCase == null) {
                finalVerdict = worseVerdict(finalVerdict, Verdict.INTERNAL_ERROR);
                continue;
            }

            Verdict verdict = resolveVerdict(executionResult, testCase);
            if (verdict == Verdict.ACCEPTED) {
                passedTests++;
                earnedWeight += testCase.weight();
            }
            finalVerdict = worseVerdict(finalVerdict, verdict);
        }

        int totalTests = testCases.size();
        double score = scoreCalculator.calculateScore(earnedWeight, totalWeight);

        if (passedTests != totalTests && finalVerdict == Verdict.ACCEPTED) {
            finalVerdict = Verdict.WRONG_ANSWER;
        }

        return new SubmissionResult(
            resolveSubmissionId(submission),
            submission.assignmentId(),
            submission.studentId(),
            score,
            finalVerdict,
            passedTests,
            totalTests
        );
    }

    public Verdict resolveVerdict(ExecutionResult executionResult, TestCase testCase) {
        if (executionResult.verdict() != Verdict.ACCEPTED) {
            return executionResult.verdict();
        }

        try {
            String expectedOutput = FileUtils.readText(testCase.expectedOutput());
            return outputComparator.matches(expectedOutput, executionResult.studentOutput())
                ? Verdict.ACCEPTED
                : Verdict.WRONG_ANSWER;
        } catch (IOException exception) {
            return Verdict.INTERNAL_ERROR;
        }
    }

    private Verdict worseVerdict(Verdict current, Verdict candidate) {
        return severity(candidate) > severity(current) ? candidate : current;
    }

    private int severity(Verdict verdict) {
        return switch (verdict) {
            case ACCEPTED -> 0;
            case WRONG_ANSWER -> 1;
            case RUNTIME_ERROR -> 2;
            case TIME_LIMIT_EXCEEDED -> 3;
            case MEMORY_LIMIT_EXCEEDED -> 4;
            case COMPILATION_ERROR -> 5;
            case INTERNAL_ERROR -> 6;
        };
    }

    private String resolveSubmissionId(Submission submission) {
        if (submission.filePath() == null || submission.filePath().getFileName() == null) {
            return "";
        }
        return submission.filePath().getFileName().toString();
    }
}
