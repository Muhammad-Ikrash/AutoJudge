package autojudge.model;

import autojudge.config.ContainerConfig;

import java.nio.file.Path;
import java.util.List;

/**
 * Context container holding input options, assignment configuration, test cases, and submission targets.
 */
public record EvaluationContext(
        Path submissionsRoot,
        Path inputDirectory,
        Path outputDirectory,
        Assignment assignment,
        ContainerConfig containerConfig,
        List<TestCase> testCases,
        List<Path> submissionFolders
) {}
