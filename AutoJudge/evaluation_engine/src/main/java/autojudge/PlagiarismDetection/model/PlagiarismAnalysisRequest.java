package autojudge.PlagiarismDetection.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Request payload encapsulating arguments for a plagiarism analysis execution.
 */
public record PlagiarismAnalysisRequest(
        String assignmentId,
        Path assignmentPath,
        PlagiarismConfig config
) {
    public PlagiarismAnalysisRequest {
        Objects.requireNonNull(assignmentId, "assignmentId must not be null");
        Objects.requireNonNull(assignmentPath, "assignmentPath must not be null");
        config = config != null ? config : PlagiarismConfig.defaultConfig();
    }
}
