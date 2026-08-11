package autojudge.docker;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaliciousCodeDetectorTest {

    private final MaliciousCodeDetector detector = new MaliciousCodeDetector();

    @Test
    void testDetectsUnauthorizedFile() {
        Set<String> before = Set.of("/workspace/main.py");
        Set<String> after = Set.of("/workspace/main.py", "/workspace/hack.sh");

        Set<String> unauthorized = detector.detectUnauthorizedFiles(before, after);
        assertTrue(unauthorized.contains("/workspace/hack.sh"));
    }

    @Test
    void testIgnoresBenignFiles() {
        Set<String> before = Set.of("/workspace/Main.java");
        Set<String> after = Set.of(
            "/workspace/Main.java",
            "/workspace/Main.class",
            "/workspace/__pycache__/solution.pyc",
            "/workspace/output.txt"
        );

        Set<String> unauthorized = detector.detectUnauthorizedFiles(before, after);
        assertTrue(unauthorized.isEmpty());
    }

    @Test
    void testIsAllowedOrBenignFile() {
        assertTrue(detector.isAllowedOrBenignFile("/path/__pycache__/mod.pyc"));
        assertTrue(detector.isAllowedOrBenignFile("/path/Solution.class"));
        assertTrue(detector.isAllowedOrBenignFile("/path/temp.tmp"));
        assertFalse(detector.isAllowedOrBenignFile("/path/exploit.bin"));
    }
}
