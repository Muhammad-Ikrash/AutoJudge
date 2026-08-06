package autojudge.model;

import java.nio.file.Path;
import java.util.List;

import autojudge.config.Language;

public record SubmissionLayout(
        Language language,
        List<Path> sourceFiles
) {}