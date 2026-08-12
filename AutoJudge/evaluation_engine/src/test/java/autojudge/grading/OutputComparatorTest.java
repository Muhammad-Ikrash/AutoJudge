package autojudge.grading;

import autojudge.CoreEvaluation.grading.OutputComparator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputComparatorTest {

    private final OutputComparator comparator = new OutputComparator();

    @Test
    void testExactMatch() {
        assertTrue(comparator.matches("hello world\n", "hello world\n"));
    }

    @Test
    void testTrailingWhitespaceAndNewlines() {
        assertTrue(comparator.matches("hello world   \r\n\r\n", "hello world\n"));
    }

    @Test
    void testMismatch() {
        assertFalse(comparator.matches("hello world", "hello earth"));
    }

    @Test
    void testNullOrEmpty() {
        assertEquals("", comparator.normalize(null));
        assertEquals("", comparator.normalize(""));
    }
}
