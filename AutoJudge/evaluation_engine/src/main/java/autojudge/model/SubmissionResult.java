package autojudge.model;

import java.util.List;

public record SubmissionResult(
    String submissionId,
    String assignmentId,
    String studentId,
    double score,
    Verdict verdict,
    int passedTests,
    int totalTests,
    List<testCaseResult> testCasesResults
) {

}
