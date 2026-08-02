package autojudge.Models ;

public record Submission {
    public Path filePath;
    public Path inputFilePath;
    public Path expectedOutputPath;
    public String StudentID;
    public String AssignmentID;
}
