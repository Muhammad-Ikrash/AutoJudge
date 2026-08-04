package autojudge.model;

import java.nio.file.Path;

import autojudge.compiler.SubmissionLayout;

public record Submission(
    Path SubmissionRootPath,
    Path expectedOutputFolderPath,
    Path inputFolderPath,
    String studentId,
    String assignmentId,
) {

    public Path getSubmissionRoot(){
        return SubmissionRootPath;
    }

}