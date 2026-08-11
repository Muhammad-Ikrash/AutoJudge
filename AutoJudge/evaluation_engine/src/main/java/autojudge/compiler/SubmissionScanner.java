package autojudge.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import autojudge.config.Language;
import autojudge.model.SubmissionLayout;

public final class SubmissionScanner {

    private SubmissionScanner() {
    }

    /**
     * Primary entry point for scanning submission directories/files.
     */
    public static SubmissionLayout scan(Path submissionRoot) throws IOException {
        if (Files.isRegularFile(submissionRoot)) {
            return scanSingleFile(submissionRoot);
        }
        return scanDirectoryTree(submissionRoot);
    }

    // =========================================================================
    // Single-Responsibility Helper Methods
    // =========================================================================
    private static SubmissionLayout scanSingleFile(Path singleFile) {
        String fileName = singleFile.getFileName().toString().toLowerCase();
        Language language = detectLanguageFromFileName(fileName);

        if (language != null && isSupportedSourceFile(fileName)) {
            return new SubmissionLayout(language, List.of(singleFile.getFileName()));
        }
        throw new IllegalArgumentException("Unsupported source file: " + singleFile);
    }

    private static SubmissionLayout scanDirectoryTree(Path submissionRoot) throws IOException {
        List<Path> sourceFiles = new ArrayList<>();
        Language[] detectedLanguageContainer = new Language[1];

        try (var stream = Files.walk(submissionRoot)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (Files.isRegularFile(file)) {
                    processFileInTree(submissionRoot, file, sourceFiles, detectedLanguageContainer);
                }
            }
        }

        Language detectedLanguage = detectedLanguageContainer[0];
        if (detectedLanguage == null) {
            throw new IllegalArgumentException("No supported language detected.");
        }

        sourceFiles.sort(Path::compareTo);
        return new SubmissionLayout(detectedLanguage, sourceFiles);
    }

    private static void processFileInTree(
            Path submissionRoot,
            Path file,
            List<Path> sourceFiles,
            Language[] detectedLanguageContainer
    ) {
        String fileName = file.getFileName().toString().toLowerCase();
        Language currentLanguage = detectLanguageFromFileName(fileName);
        if (currentLanguage == null) {
            return;
        }

        updateAndValidateLanguage(currentLanguage, detectedLanguageContainer);

        if (isSupportedSourceFile(fileName)) {
            sourceFiles.add(submissionRoot.relativize(file));
        }
    }

    private static void updateAndValidateLanguage(
            Language currentLanguage,
            Language[] detectedLanguageContainer
    ) {
        if (detectedLanguageContainer[0] == null) {
            detectedLanguageContainer[0] = currentLanguage;
        } else if (detectedLanguageContainer[0] != currentLanguage) {
            throw new IllegalArgumentException("Submission contains multiple programming languages.");
        }
    }

    private static Language detectLanguageFromFileName(String fileName) {
        return Language.fromFileName(fileName);
    }

    private static boolean isSupportedSourceFile(String fileName) {
        return Language.isSupportedSourceFile(fileName);
    }
}