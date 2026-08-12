package autojudge.CoreEvaluation.model;

import java.nio.file.Path;

public record Submission(
    Path filePath,
    Path inputFilePath,
    Path expectedOutputFilePath,
    String studentId,
    String assignmentId
) {

    public Path getSubmissionRoot() {
        return filePath;
    }
}