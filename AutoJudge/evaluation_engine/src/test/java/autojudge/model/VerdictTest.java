package autojudge.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerdictTest {

    @Test
    void testSeverityHierarchy() {
        assertTrue(Verdict.MALICIOUS_CODE.isMoreSevereThan(Verdict.INTERNAL_ERROR));
        assertTrue(Verdict.INTERNAL_ERROR.isMoreSevereThan(Verdict.COMPILATION_ERROR));
        assertTrue(Verdict.COMPILATION_ERROR.isMoreSevereThan(Verdict.MEMORY_LIMIT_EXCEEDED));
        assertTrue(Verdict.MEMORY_LIMIT_EXCEEDED.isMoreSevereThan(Verdict.TIME_LIMIT_EXCEEDED));
        assertTrue(Verdict.TIME_LIMIT_EXCEEDED.isMoreSevereThan(Verdict.RUNTIME_ERROR));
        assertTrue(Verdict.RUNTIME_ERROR.isMoreSevereThan(Verdict.WRONG_ANSWER));
        assertTrue(Verdict.WRONG_ANSWER.isMoreSevereThan(Verdict.ACCEPTED));
    }

    @Test
    void testIsMoreSevereThanNull() {
        assertTrue(Verdict.ACCEPTED.isMoreSevereThan(null));
    }

    @Test
    void testIsMoreSevereThanEqual() {
        assertFalse(Verdict.WRONG_ANSWER.isMoreSevereThan(Verdict.WRONG_ANSWER));
    }
}
