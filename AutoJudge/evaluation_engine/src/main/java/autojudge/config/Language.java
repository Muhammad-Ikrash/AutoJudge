package autojudge.config;

import java.util.Arrays;
import java.util.List;

/**
 * Supported programming languages for evaluation with associated file extensions.
 */
public enum Language {
    CPP(List.of(".cpp", ".cc", ".cxx", ".hpp", ".h")),
    C(List.of(".c")),
    JAVA(List.of(".java")),
    PYTHON(List.of(".py"));

    private final List<String> extensions;

    Language(List<String> extensions) {
        this.extensions = extensions;
    }

    public List<String> getExtensions() {
        return extensions;
    }

    public static Language fromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        String lower = fileName.toLowerCase();
        return Arrays.stream(values())
                .filter(lang -> lang.extensions.stream().anyMatch(lower::endsWith))
                .findFirst()
                .orElse(null);
    }

    public static boolean isSupportedSourceFile(String fileName) {
        return fromFileName(fileName) != null;
    }
}