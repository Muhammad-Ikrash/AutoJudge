package autojudge.CoreEvaluation.model;

import java.io.Serializable;
import java.util.List;

public record SubmissionResult(
        String submissionId,
        String assignmentId,
        String studentId,
        double score,
        Verdict verdict,
        int passedTests,
        int totalTests,
        List<testCaseResult> testCasesResults,
        String batchId,
        int totalSubmissionsInBatch
) implements Serializable {

    // Overloaded constructor for backwards compatibility where batch details are optional
    public SubmissionResult(
            String submissionId,
            String assignmentId,
            String studentId,
            double score,
            Verdict verdict,
            int passedTests,
            int totalTests,
            List<testCaseResult> testCasesResults
    ) {
        this(submissionId, assignmentId, studentId, score, verdict, passedTests, totalTests, testCasesResults, "default-batch", 1);
    }
}
