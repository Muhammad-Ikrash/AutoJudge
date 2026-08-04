package autojudge.compiler;

import java.nio.file.Path;
import java.util.List;

public record SubmissionLayout(
        Language language,
        List<Path> sourceFiles
) {}