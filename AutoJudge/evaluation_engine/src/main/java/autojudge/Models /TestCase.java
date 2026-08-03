package autojudge.Models ;

import java.nio.file.Path;

public record TestCase (
    String id,
    Path inputFile,
    Path expectedOutput,
    int weight
) {
    
};