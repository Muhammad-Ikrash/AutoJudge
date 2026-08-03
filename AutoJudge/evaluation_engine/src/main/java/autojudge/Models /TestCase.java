package autojudge.Models ;

public record TestCase (
    String id,
    Path inputFile,
    Path expectedOutput,
    int weight
) {
    
};