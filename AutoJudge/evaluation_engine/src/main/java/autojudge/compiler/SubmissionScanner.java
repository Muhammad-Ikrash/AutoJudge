package autojudge.compiler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SubmissionScanner {

    private SubmissionScanner() {
    }

    public static SubmissionLayout scan(Path submissionRoot) throws IOException {
        List<Path> sourceFiles = new ArrayList<>();
        Language detectedLanguage = null;

        if (Files.isRegularFile(submissionRoot)) {
            String name = submissionRoot.getFileName().toString().toLowerCase();
            Language lang = getLanguage(name);
            if (lang != null && isSourceFile(name)) {
                return new SubmissionLayout(lang, List.of(submissionRoot.getFileName()));
            }
            throw new IllegalArgumentException("Unsupported source file: " + submissionRoot);
        }

        try (var stream = Files.walk(submissionRoot)) {
            for (Path file : (Iterable<Path>) stream::iterator) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }

                String name = file.getFileName().toString().toLowerCase();
                Language current = getLanguage(name);
                if (current == null) {
                    continue;
                }

                if (detectedLanguage == null) {
                    detectedLanguage = current;
                } else if (detectedLanguage != current) {
                    throw new IllegalArgumentException("Submission contains multiple programming languages.");
                }

                if (isSourceFile(name)) {
                    sourceFiles.add(submissionRoot.relativize(file));
                }
            }
        }

        if (detectedLanguage == null) {
            throw new IllegalArgumentException("No supported language detected.");
        }

        sourceFiles.sort(Path::compareTo);
        return new SubmissionLayout(detectedLanguage, sourceFiles);
    }

    private static Language getLanguage(String fileName) {
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

    private static boolean isSourceFile(String fileName) {
        return fileName.endsWith(".cpp")
                || fileName.endsWith(".cc")
                || fileName.endsWith(".cxx")
                || fileName.endsWith(".c")
                || fileName.endsWith(".java")
                || fileName.endsWith(".py");
    }
}