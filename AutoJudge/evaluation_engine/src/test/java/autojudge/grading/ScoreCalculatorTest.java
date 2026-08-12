package autojudge.grading;

import autojudge.CoreEvaluation.grading.ScoreCalculator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreCalculatorTest {

    private final ScoreCalculator calculator = new ScoreCalculator();

    @Test
    void testFullScore() {
        assertEquals(100.0, calculator.calculateScore(10, 10));
    }

    @Test
    void testPartialScore() {
        assertEquals(50.0, calculator.calculateScore(5, 10));
        assertEquals(75.0, calculator.calculateScore(3, 4));
    }

    @Test
    void testZeroTotalWeight() {
        assertEquals(0.0, calculator.calculateScore(5, 0));
    }
}
