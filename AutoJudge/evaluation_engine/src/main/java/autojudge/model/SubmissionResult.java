package autojudge.model;

public record SubmissionResult(
    String submissionId,
    String assignmentId,
    String studentId,
    double score,
    Verdict verdict,
    int passedTests,
    int totalTests
) {
}
