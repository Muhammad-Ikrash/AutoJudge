package autojudge.CoreEvaluation.loader;
import autojudge.CoreEvaluation.model.TestCase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers and pairs input/output test case files, assigning corresponding weights.
 */
public class TestCaseFileProcessor {

    private static final Logger log = LoggerFactory.getLogger(TestCaseFileProcessor.class);

    public List<TestCase> processTestCases(
            Path inputDirectory,
            Path outputDirectory,
            Map<String, Integer> weightsByTestCase
    ) throws IOException {
        List<Path> inputFiles = listFilesInDirectory(inputDirectory);
        List<Path> outputFiles = listFilesInDirectory(outputDirectory);

        if (inputFiles.isEmpty()) {
            log.warn("No test case input files found in {}", inputDirectory);
            return List.of();
        }

        Map<String, Path> outputsByNormalizedKey = new HashMap<>();
        for (Path outFile : outputFiles) {
            String rawStem = fileStem(outFile.getFileName().toString());
            outputsByNormalizedKey.put(rawStem, outFile);
            String normStem = normalizeStem(rawStem);
            if (!normStem.isEmpty()) {
                outputsByNormalizedKey.putIfAbsent(normStem, outFile);
            }
        }

        List<TestCase> testCases = new ArrayList<>();
        for (int i = 0; i < inputFiles.size(); i++) {
            Path inFile = inputFiles.get(i);
            String rawStem = fileStem(inFile.getFileName().toString());
            String normStem = normalizeStem(rawStem);

            Path matchedOutFile = outputsByNormalizedKey.get(rawStem);
            if (matchedOutFile == null && !normStem.isEmpty()) {
                matchedOutFile = outputsByNormalizedKey.get(normStem);
            }
            if (matchedOutFile == null && i < outputFiles.size()) {
                matchedOutFile = outputFiles.get(i);
                log.info("Matched input {} with output {} by index fallback", inFile.getFileName(), matchedOutFile.getFileName());
            }

            if (matchedOutFile == null) {
                log.warn("Could not find matching output file for input case {}", rawStem);
            }

            int weight = weightsByTestCase.getOrDefault(rawStem, weightsByTestCase.getOrDefault(normStem, 1));
            testCases.add(new TestCase(rawStem, inFile, matchedOutFile, weight));
        }

        log.info("Successfully loaded and paired {} test cases.", testCases.size());
        return testCases;
    }

    private List<Path> listFilesInDirectory(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    public static String fileStem(String fileName) {
        if (fileName == null) return "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    public static String normalizeStem(String stem) {
        if (stem == null) return "";
        return stem.toLowerCase()
                .replace("test_input", "")
                .replace("test_output", "")
                .replace("input", "")
                .replace("output", "")
                .trim();
    }
}
