package autojudge.docker;

import autojudge.model.ExecCMD;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputValidatorTest {

    private final OutputValidator validator = new OutputValidator(20);

    @Test
    void testTruncateIfNeededShort() {
        assertEquals("hello", validator.truncateIfNeeded("hello"));
    }

    @Test
    void testTruncateIfNeededLong() {
        String longOutput = "1234567890123456789012345";
        String truncated = validator.truncateIfNeeded(longOutput);
        assertTrue(truncated.startsWith("12345678901234567890"));
        assertTrue(truncated.contains("[Output truncated]"));
    }

    @Test
    void testIsOutputExceeded() {
        ExecCMD normal = new ExecCMD(0, "short", "");
        assertFalse(validator.isOutputExceeded(normal));

        ExecCMD exceeded = new ExecCMD(0, "1234567890123456789012345", "");
        assertTrue(validator.isOutputExceeded(exceeded));
    }
}
