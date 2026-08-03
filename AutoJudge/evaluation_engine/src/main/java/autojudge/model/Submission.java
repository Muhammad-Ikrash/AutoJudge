package autojudge.model;

import java.nio.file.Path;

public record Submission(
    Path filePath,
    Path inputFilePath,
    Path expectedOutputFilePath,
    String studentId,
    String assignmentId
) {
}