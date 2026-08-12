package autojudge.CoreEvaluation.model;

import java.nio.file.Path;
import java.util.List;

import autojudge.CoreEvaluation.config.Language;

public record SubmissionLayout(
        Language language,
        List<Path> sourceFiles
) {}