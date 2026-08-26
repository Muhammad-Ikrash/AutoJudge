package autojudge.PlagiarismDetection.model;

import java.util.List;

/**
 * Domain model representing the complete aggregated plagiarism report for an assignment.
 */
public record PlagiarismReport(
        String assignmentId,
        List<SimilarityPair> similarities
) {}
