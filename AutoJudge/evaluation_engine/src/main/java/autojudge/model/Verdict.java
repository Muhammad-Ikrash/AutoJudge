package autojudge.model;

/**
 * Execution and grading verdicts with defined severity hierarchy.
 */
public enum Verdict {
    ACCEPTED(0),
    WRONG_ANSWER(1),
    RUNTIME_ERROR(2),
    TIME_LIMIT_EXCEEDED(3),
    MEMORY_LIMIT_EXCEEDED(4),
    COMPILATION_ERROR(5),
    INTERNAL_ERROR(6),
    MALICIOUS_CODE(7);

    private final int severity;

    Verdict(int severity) {
        this.severity = severity;
    }

    public int getSeverity() {
        return severity;
    }

    public boolean isMoreSevereThan(Verdict other) {
        if (other == null) {
            return true;
        }
        return this.severity > other.severity;
    }
}
