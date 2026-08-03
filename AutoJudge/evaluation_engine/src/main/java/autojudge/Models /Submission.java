package autojudge.Models ;

import java.nio.file.Path;

public record Submission (
    Path filePath,
    Path inputFilePath,
    Path ExpectedOutputFilePath,
    String StudentID,
    String AssignmentID
) {

};