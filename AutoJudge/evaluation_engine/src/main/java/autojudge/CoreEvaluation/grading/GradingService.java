package autojudge.CoreEvaluation.grading;

import autojudge.CoreEvaluation.model.ExecutionResult;
import autojudge.CoreEvaluation.model.Submission;
import autojudge.CoreEvaluation.model.SubmissionResult;
import autojudge.CoreEvaluation.model.TestCase;
import autojudge.CoreEvaluation.model.Verdict;
import autojudge.CoreEvaluation.model.testCaseResult;
import autojudge.CoreEvaluation.util.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GradingService {

    private static final Logger log = LoggerFactory.getLogger(GradingService.class);

    private final OutputComparator outputComparator;
    private final ScoreCalculator scoreCalculator;

    public GradingService() {
        this(new OutputComparator(), new ScoreCalculator());
    }

    public GradingService(OutputComparator outputComparator, ScoreCalculator scoreCalculator) {
        this.outputComparator = outputComparator;
        this.scoreCalculator = scoreCalculator;
    }

    /**
     * Primary grading entry point. Reads like top-level pseudocode.
     */
    public SubmissionResult grade(
        Submission submission,
        List<TestCase> testCases,
        List<ExecutionResult> executionResults
    ) {
        Map<String, TestCase> testCaseById = indexTestCases(testCases);
        int totalWeight = calculateTotalWeight(testCases);

        EvaluationGrade grade = evaluateAllResults(testCaseById, executionResults);

        double score = calculateFinalScore(grade.finalVerdict(), grade.earnedWeight(), totalWeight);
        Verdict finalVerdict = determineFinalVerdict(grade.finalVerdict(), grade.passedTests(), testCases.size());

        return buildSubmissionResult(submission, score, finalVerdict, grade.passedTests(), testCases.size(), grade.testCaseMapping());
    }

    // =========================================================================
    // Single-Responsibility Helper Methods
    // =========================================================================
    private Map<String, TestCase> indexTestCases(List<TestCase> testCases) {
        Map<String, TestCase> map = new HashMap<>();
        for (TestCase testCase : testCases) {
            if (testCase != null && testCase.id() != null) {
                map.putIfAbsent(testCase.id(), testCase);
            }
        }
        return map;
    }

    private int calculateTotalWeight(List<TestCase> testCases) {
        int total = 0;
        for (TestCase testCase : testCases) {
            if (testCase != null) {
                total += testCase.weight();
            }
        }
        return total;
    }

    private EvaluationGrade evaluateAllResults(
            Map<String, TestCase> testCaseById,
            List<ExecutionResult> executionResults
    ) {
        int passedTests = 0;
        int earnedWeight = 0;
        Verdict worstVerdict = Verdict.ACCEPTED;

        List<testCaseResult> testcaseMapping = new ArrayList<>(); // to map the testcases to the verdict to return 
        
        for (ExecutionResult executionResult : executionResults) {
            TestCase testCase = testCaseById.get(executionResult.testCaseId());
            if (testCase == null) {
                worstVerdict = resolveWorseVerdict(worstVerdict, Verdict.INTERNAL_ERROR);
                continue;
            }

            Verdict verdict = resolveVerdict(executionResult, testCase);
            if (verdict == Verdict.ACCEPTED) {
                passedTests++;
                earnedWeight += testCase.weight();
            }
            worstVerdict = resolveWorseVerdict(worstVerdict, verdict);

            testcaseMapping.add(new testCaseResult(testCase.id(), verdict));

            log.info("[TestCase: {}] Verdict: {} | Time: {} ms", testCase.id(), verdict, executionResult.executionTime());

        }

        return new EvaluationGrade(passedTests, earnedWeight, worstVerdict, testcaseMapping);
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

    private double calculateFinalScore(Verdict finalVerdict, int earnedWeight, int totalWeight) {
        if (finalVerdict == Verdict.MALICIOUS_CODE) {
            return 0.0;
        }
        return scoreCalculator.calculateScore(earnedWeight, totalWeight);
    }

    private Verdict determineFinalVerdict(Verdict currentWorst, int passedTests, int totalTests) {
        if (passedTests != totalTests && currentWorst == Verdict.ACCEPTED) {
            return Verdict.WRONG_ANSWER;
        }
        return currentWorst;
    }

    private Verdict resolveWorseVerdict(Verdict current, Verdict candidate) {
        return candidate.isMoreSevereThan(current) ? candidate : current;
    }

    private SubmissionResult buildSubmissionResult(
            Submission submission,
            double score,
            Verdict finalVerdict,
            int passedTests,
            int totalTests,
            List<testCaseResult> testCaseMapping
    ) {
        String submissionId = (submission.filePath() != null && submission.filePath().getFileName() != null)
                ? submission.filePath().getFileName().toString()
                : "";

        return new SubmissionResult(
            submissionId,
            submission.assignmentId(),
            submission.studentId(),
            score,
            finalVerdict,
            passedTests,
            totalTests,
            testCaseMapping
        );
    }

    // Helper record for intermediate grade accumulation
    private record EvaluationGrade(int passedTests, int earnedWeight, Verdict finalVerdict, List<testCaseResult> testCaseMapping) {}
}
