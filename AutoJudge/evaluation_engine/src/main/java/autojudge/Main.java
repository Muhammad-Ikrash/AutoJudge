package autojudge;

import autojudge.compiler.Language;
import autojudge.docker.ContainerConfig;
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
import java.util.LinkedHashMap;
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
        if (args.length != EXPECTED_ARGUMENT_COUNT) {
            printUsage();
            return;
        }

        Path submissionsRoot = Path.of(args[0]);
        Path inputDirectory = Path.of(args[1]);
        Path outputDirectory = Path.of(args[2]);
        Path configFile = Path.of(args[3]);
        Path weightsFile = Path.of(args[4]);

        Assignment assignment = AssignmentLoader.loadConfig(configFile);
        Language language = resolveLanguage(assignment.executionProfile().language());
        Map<String, Integer> weightsByTestCase = loadWeights(weightsFile);
        List<TestCase> testCases = loadTestCases(inputDirectory, outputDirectory, weightsByTestCase);

        ContainerConfig containerConfig = ContainerConfig.from(assignment, resolveImage(language));
        DockerRunner dockerRunner = new DockerRunner();
        GradingService gradingService = new GradingService();

        List<Path> submissionFolders = listSubmissionFolders(submissionsRoot);
        List<SubmissionResult> results = new ArrayList<>();

        for (Path submissionFolder : submissionFolders) {
            String studentId = resolveStudentId(submissionFolder);
            Submission submission = SubmissionLoader.load(
                submissionFolder,
                inputDirectory,
                outputDirectory,
                studentId,
                assignment.assignmentId()
            );

            List<ExecutionResult> executionResults = dockerRunner.runSubmission(containerConfig, submission, testCases);
            results.add(gradingService.grade(submission, testCases, executionResults));
        }

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
        Map<String, Path> inputsByStem = indexFilesByStem(inputDirectory);
        Map<String, Path> outputsByStem = indexFilesByStem(outputDirectory);

        List<TestCase> testCases = new ArrayList<>();
        for (Map.Entry<String, Path> entry : inputsByStem.entrySet()) {
            String testCaseId = entry.getKey();
            Path inputFile = entry.getValue();
            Path expectedOutput = outputsByStem.getOrDefault(
                testCaseId,
                outputDirectory.resolve(resolveFileName(testCaseId, ".out"))
            );
            int weight = weightsByTestCase.getOrDefault(testCaseId, 1);
            testCases.add(new TestCase(testCaseId, inputFile, expectedOutput, weight));
        }

        return testCases;
    }

    private static Map<String, Path> indexFilesByStem(Path directory) throws IOException {
        Map<String, Path> filesByStem = new LinkedHashMap<>();
        if (!Files.isDirectory(directory)) {
            return filesByStem;
        }

        List<Path> files = new ArrayList<>();
        try (var stream = Files.list(directory)) {
            stream
                .filter(Files::isRegularFile)
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .forEach(files::add);
        }

        for (Path file : files) {
            filesByStem.putIfAbsent(fileStem(file.getFileName().toString()), file);
        }
        return filesByStem;
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

    private static Language resolveLanguage(String lang) {
        if (lang == null) {
            return Language.CPP;
        }
        String lower = lang.trim().toLowerCase();
        return switch (lower) {
            case "cpp", "c++", "cxx", "cc" -> Language.CPP;
            case "c" -> Language.C;
            case "java" -> Language.JAVA;
            case "python", "py", "python3" -> Language.PYTHON;
            default -> Language.CPP;
        };
    }

    private static String resolveImage(Language language) {
        return switch (language) {
            case CPP, C -> "gcc:13";
            case JAVA -> "eclipse-temurin:21-jdk";
            case PYTHON -> "python:3.12-slim";
        };
    }

    private static String resolveStudentId(Path submissionFolder) {
        if (submissionFolder.getFileName() == null) {
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

    private static String resolveFileName(String stem, String extension) {
        return stem + extension;
    }
}
