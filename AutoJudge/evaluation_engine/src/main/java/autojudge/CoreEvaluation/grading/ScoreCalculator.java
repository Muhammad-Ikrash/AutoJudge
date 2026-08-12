package autojudge.CoreEvaluation.grading;

public final class ScoreCalculator {

    public double calculateScore(int earnedWeight, int totalWeight) {
        if (totalWeight <= 0) {
            return 0.0d;
        }
        return (earnedWeight * 100.0d) / totalWeight;
    }
}
