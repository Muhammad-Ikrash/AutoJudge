package autojudge;

import autojudge.config.ContainerConfig;
import autojudge.docker.DockerRunner;
import autojudge.grading.GradingService;
import autojudge.loader.AssignmentLoader;
import autojudge.loader.SubmissionLoader;
import autojudge.model.Assignment;
import autojudge.model.ExecutionResult;
import autojudge.model.Submission;
import autojudge.model.SubmissionResult;
import autojudge.model.TestCase;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Main {

    private static final int EXPECTED_ARGUMENT_COUNT = 5;
    private static final Pattern WEIGHT_JSON_PATTERN = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(\\d+)");

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (!hasValidArgumentCount(args)) {
            printUsage();
            return;
        }

        EvaluationContext context = parseEvaluationContext(args);
        List<SubmissionResult> results = evaluateAllSubmissions(context);
        printSubmissionResults(results);
    }

    private static boolean hasValidArgumentCount(String[] args) {
        return args != null && args.length == EXPECTED_ARGUMENT_COUNT;
    }

    private static EvaluationContext parseEvaluationContext(String[] args) throws Exception {
        Path submissionsRoot = Path.of(args[0]);
        Path inputDirectory = Path.of(args[1]);
        Path outputDirectory = Path.of(args[2]);
        Path configFile = Path.of(args[3]);
        Path weightsFile = Path.of(args[4]);

        Assignment assignment = AssignmentLoader.loadConfig(configFile);
        Map<String, Integer> weightsByTestCase = loadWeights(weightsFile);
        List<TestCase> testCases = loadTestCases(inputDirectory, outputDirectory, weightsByTestCase);
        ContainerConfig containerConfig = ContainerConfig.from(assignment);
        List<Path> submissionFolders = listSubmissionFolders(submissionsRoot);

        return new EvaluationContext(
                submissionsRoot,
                inputDirectory,
                outputDirectory,
                assignment,
                containerConfig,
                testCases,
                submissionFolders
        );
    }

    private static List<SubmissionResult> evaluateAllSubmissions(EvaluationContext context) throws Exception {
        DockerRunner dockerRunner = new DockerRunner();
        GradingService gradingService = new GradingService();
        List<SubmissionResult> results = new ArrayList<>();

        for (Path submissionFolder : context.submissionFolders()) {
            SubmissionResult result = evaluateSingleSubmission(context, submissionFolder, dockerRunner, gradingService);
            results.add(result);
        }

        return results;
    }

    private static SubmissionResult evaluateSingleSubmission(
            EvaluationContext context,
            Path submissionFolder,
            DockerRunner dockerRunner,
            GradingService gradingService
    ) throws Exception {
        String studentId = resolveStudentId(submissionFolder);
        Submission submission = SubmissionLoader.load(
                submissionFolder,
                context.inputDirectory(),
                context.outputDirectory(),
                studentId,
                context.assignment().assignmentId()
        );

        List<ExecutionResult> executionResults = dockerRunner.runSubmission(
                context.containerConfig(), submission, context.testCases()
        );

        return gradingService.grade(submission, context.testCases(), executionResults);
    }

    private static void printSubmissionResults(List<SubmissionResult> results) {
        for (SubmissionResult result : results) {
            System.out.println(result);
        }
    }

    private static void printUsage() {
        System.err.println("Usage: <submissionsRoot> <inputDirectory> <outputDirectory> <configFile> <weightsFile>");
    }

    private static List<Path> listSubmissionFolders(Path submissionsRoot) throws IOException {
        List<Path> submissionFolders = new ArrayList<>();
        if (!Files.exists(submissionsRoot)) {
            return submissionFolders;
        }

        if (Files.isDirectory(submissionsRoot)) {
            try (var stream = Files.list(submissionsRoot)) {
                stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(submissionFolders::add);
            }
        }

        if (submissionFolders.isEmpty()) {
            submissionFolders.add(submissionsRoot);
        }
        return submissionFolders;
    }

    private static List<TestCase> loadTestCases(
        Path inputDirectory,
        Path outputDirectory,
        Map<String, Integer> weightsByTestCase
    ) throws IOException {
        List<Path> inputFiles = listFilesInDirectory(inputDirectory);
        List<Path> outputFiles = listFilesInDirectory(outputDirectory);

        Map<String, Path> outputsByNormalizedKey = new HashMap<>();
        for (Path outFile : outputFiles) {
            String rawStem = fileStem(outFile.getFileName().toString());
            outputsByNormalizedKey.put(rawStem, outFile);
            outputsByNormalizedKey.put(normalizeStem(rawStem), outFile);
        }

        List<TestCase> testCases = new ArrayList<>();
        for (int i = 0; i < inputFiles.size(); i++) {
            Path inFile = inputFiles.get(i);
            String rawStem = fileStem(inFile.getFileName().toString());
            String normStem = normalizeStem(rawStem);

            Path matchedOutFile = outputsByNormalizedKey.get(rawStem);
            if (matchedOutFile == null) {
                matchedOutFile = outputsByNormalizedKey.get(normStem);
            }
            if (matchedOutFile == null && i < outputFiles.size()) {
                matchedOutFile = outputFiles.get(i);
            }

            int weight = weightsByTestCase.getOrDefault(rawStem, weightsByTestCase.getOrDefault(normStem, 1));
            testCases.add(new TestCase(rawStem, inFile, matchedOutFile, weight));
        }

        return testCases;
    }

    private static List<Path> listFilesInDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var stream = Files.list(directory)) {
            return stream
                .filter(Files::isRegularFile)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        }
    }

    private static String normalizeStem(String stem) {
        if (stem == null) return "";
        return stem.toLowerCase()
                .replace("test_input", "")
                .replace("test_output", "")
                .replace("input", "")
                .replace("output", "")
                .trim();
    }

    private static Map<String, Integer> loadWeights(Path weightsFile) throws IOException {
        Map<String, Integer> weights = new HashMap<>();
        if (!Files.exists(weightsFile)) {
            return weights;
        }

        String content = Files.readString(weightsFile);
        Matcher jsonMatcher = WEIGHT_JSON_PATTERN.matcher(content);
        while (jsonMatcher.find()) {
            weights.put(jsonMatcher.group(1), Integer.parseInt(jsonMatcher.group(2)));
        }

        if (!weights.isEmpty()) {
            return weights;
        }

        for (String line : content.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            String[] parts = trimmed.split("[,:=\\s]+", 2);
            if (parts.length == 2) {
                weights.put(parts[0].trim(), Integer.parseInt(parts[1].trim()));
            }
        }

        return weights;
    }

    private static String resolveStudentId(Path submissionFolder) {
        if (submissionFolder == null || submissionFolder.getFileName() == null) {
            return "";
        }
        return submissionFolder.getFileName().toString();
    }

    private static String fileStem(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    private record EvaluationContext(
            Path submissionsRoot,
            Path inputDirectory,
            Path outputDirectory,
            Assignment assignment,
            ContainerConfig containerConfig,
            List<TestCase> testCases,
            List<Path> submissionFolders
    ) {}
}
