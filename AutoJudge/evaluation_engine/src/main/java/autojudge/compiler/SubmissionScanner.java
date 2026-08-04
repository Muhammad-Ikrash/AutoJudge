package autojudge.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
        if (fileName.endsWith(".cpp") || fileName.endsWith(".cc") || fileName.endsWith(".cxx") || fileName.endsWith(".hpp") || fileName.endsWith(".h")) {
            return Language.CPP;
        }
        if (fileName.endsWith(".c")) {
            return Language.C;
        }
        if (fileName.endsWith(".java")) {
            return Language.JAVA;
        }
        if (fileName.endsWith(".py")) {
            return Language.PYTHON;
        }
        return null;
    }

    private static boolean isSupportedSourceFile(String fileName) {
        return fileName.endsWith(".cpp")
                || fileName.endsWith(".cc")
                || fileName.endsWith(".cxx")
                || fileName.endsWith(".c")
                || fileName.endsWith(".java")
                || fileName.endsWith(".py");
    }
}