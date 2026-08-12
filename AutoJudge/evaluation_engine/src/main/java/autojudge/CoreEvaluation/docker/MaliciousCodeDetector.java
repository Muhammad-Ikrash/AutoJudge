package autojudge.CoreEvaluation.docker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Detects malicious file system modifications during test case execution.
 */
public class MaliciousCodeDetector {

    private static final Logger log = LoggerFactory.getLogger(MaliciousCodeDetector.class);

    public Set<String> detectUnauthorizedFiles(Set<String> filesBefore, Set<String> filesAfter) {
        if (filesBefore == null || filesAfter == null) {
            return Set.of();
        }
        Set<String> createdFiles = new HashSet<>(filesAfter);
        createdFiles.removeAll(filesBefore);

        Set<String> unauthorized = createdFiles.stream()
                .filter(f -> !isAllowedOrBenignFile(f))
                .collect(Collectors.toSet());

        if (!unauthorized.isEmpty()) {
            log.warn("Malicious activity detected: unauthorized file creation {}", unauthorized);
        }

        return unauthorized;
    }

    public boolean isAllowedOrBenignFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return true;
        }
        String lower = filePath.toLowerCase();

        if (lower.contains("__pycache__") || lower.endsWith(".pyc") || lower.endsWith(".pyo")) {
            return true;
        }
        if (lower.endsWith(".class") || lower.contains("hs_err_pid")) {
            return true;
        }
        if (lower.endsWith(".tmp") || lower.endsWith(".log") || lower.endsWith(".out") || lower.endsWith(".txt")) {
            return true;
        }
        return false;
    }
}
