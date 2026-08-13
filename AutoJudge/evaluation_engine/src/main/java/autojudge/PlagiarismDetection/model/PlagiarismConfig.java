package autojudge.PlagiarismDetection.model;

import java.util.List;

/**
 * Configuration options for plagiarism detection.
 */
public record PlagiarismConfig(
        String language,
        double suspiciousThreshold,
        List<String> ignoredFiles
) {
    public static PlagiarismConfig defaultConfig() {
        return new PlagiarismConfig("cpp", 0.5, List.of());
    }
}
