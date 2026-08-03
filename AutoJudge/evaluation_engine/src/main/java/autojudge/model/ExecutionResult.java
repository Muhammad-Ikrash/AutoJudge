package autojudge.model;

public record ExecutionResult(
    String testCaseId,
    Verdict verdict,
    String studentOutput,
    String expectedOutput,
    String stderr,
    int exitCode,
    long executionTime,
    long memoryUsed
) {
}