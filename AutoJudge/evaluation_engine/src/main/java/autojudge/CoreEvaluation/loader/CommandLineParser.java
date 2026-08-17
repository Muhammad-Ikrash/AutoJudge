package autojudge.CoreEvaluation.loader;

import autojudge.CoreEvaluation.config.ContainerConfig;
import autojudge.CoreEvaluation.exception.ConfigurationException;
import autojudge.CoreEvaluation.model.Assignment;
import autojudge.CoreEvaluation.model.EvaluationContext;
import autojudge.CoreEvaluation.model.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Parses and validates CLI command line arguments into an EvaluationContext.
 */
public class CommandLineParser {

    private static final Logger log = LoggerFactory.getLogger(CommandLineParser.class);
    private static final int EXPECTED_ARGUMENT_COUNT = 5;

    private final WeightsFileParser weightsFileParser;
    private final TestCaseFileProcessor testCaseFileProcessor;

    public CommandLineParser() {
        this(new WeightsFileParser(), new TestCaseFileProcessor());
    }

    public CommandLineParser(WeightsFileParser weightsFileParser, TestCaseFileProcessor testCaseFileProcessor) {
        this.weightsFileParser = weightsFileParser;
        this.testCaseFileProcessor = testCaseFileProcessor;
    }

    public EvaluationContext parse(String[] args) throws IOException, ConfigurationException {
        if (args == null || args.length != EXPECTED_ARGUMENT_COUNT) {
            throw new ConfigurationException(
                "Invalid argument count. Expected " + EXPECTED_ARGUMENT_COUNT + " arguments: "
                + "<submissionsRoot> <inputDirectory> <outputDirectory> <configFile> <weightsFile>"
            );
        }

        Path submissionPath = Path.of(args[0]);
        Path inputDirectory = Path.of(args[1]);
        Path outputDirectory = Path.of(args[2]);
        Path configFile = Path.of(args[3]);
        Path weightsFile = Path.of(args[4]);

        validateInputs(submissionPath, inputDirectory, outputDirectory, configFile);

        Assignment assignment = AssignmentLoader.loadConfig(configFile);
        Map<String, Integer> weightsByTestCase = weightsFileParser.parse(weightsFile);
        List<TestCase> testCases = testCaseFileProcessor.processTestCases(inputDirectory, outputDirectory, weightsByTestCase);
        ContainerConfig containerConfig = ContainerConfig.from(assignment);

        return new EvaluationContext(
                submissionPath,
                inputDirectory,
                outputDirectory,
                assignment,
                containerConfig,
                testCases
        );
    }

    private void validateInputs(
            Path submissionsRoot,
            Path inputDirectory,
            Path outputDirectory,
            Path configFile
    ) throws ConfigurationException {
        if (!Files.exists(submissionsRoot)) {
            throw new ConfigurationException("Submissions root path does not exist: " + submissionsRoot);
        }
        if (!Files.exists(inputDirectory)) {
            throw new ConfigurationException("Input directory path does not exist: " + inputDirectory);
        }
        if (!Files.exists(outputDirectory)) {
            throw new ConfigurationException("Output directory path does not exist: " + outputDirectory);
        }
        if (!Files.exists(configFile)) {
            throw new ConfigurationException("Assignment config file path does not exist: " + configFile);
        }
    }

    // private List<Path> listSubmissionFolders(Path submissionsRoot) throws IOException {
    //     List<Path> submissionFolders = new ArrayList<>();
    //     if (!Files.exists(submissionsRoot)) {
    //         return submissionFolders;
    //     }

    //     if (Files.isDirectory(submissionsRoot)) {
    //         try (var stream = Files.list(submissionsRoot)) {
    //             stream
    //                 .filter(Files::isDirectory)
    //                 .sorted(Comparator.comparing(path -> path.getFileName().toString()))
    //                 .forEach(submissionFolders::add);
    //         }
    //     }

    //     if (submissionFolders.isEmpty()) {
    //         submissionFolders.add(submissionsRoot);
    //     }
    //     return submissionFolders;
    // }
}
