package autojudge.PlagiarismDetection.model;

/**
 * Domain model representing a similarity measurement between two student submissions.
 */
public record SimilarityPair(
        String submissionA,
        String submissionB,
        double similarity
) {}
