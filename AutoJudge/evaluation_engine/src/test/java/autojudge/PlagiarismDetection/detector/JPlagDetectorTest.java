package autojudge.PlagiarismDetection.detector;

import autojudge.PlagiarismDetection.model.PlagiarismAnalysisRequest;
import autojudge.PlagiarismDetection.model.PlagiarismConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JPlagDetectorTest {

    @Test
    void testUnsupportedLanguage_throwsUnsupportedOperationException(@TempDir Path tempDir) {
        JPlagDetector detector = new JPlagDetector();
        PlagiarismAnalysisRequest request = new PlagiarismAnalysisRequest(
                "assign-1", tempDir, "python", PlagiarismConfig.defaultConfig()
        );

        assertThrows(UnsupportedOperationException.class, () -> detector.analyze(request));
    }

    @Test
    void testSupportedLanguages_doNotThrowUnsupportedOperationException(@TempDir Path tempDir) {
        JPlagDetector detector = new JPlagDetector();

        PlagiarismAnalysisRequest cppRequest = new PlagiarismAnalysisRequest(
                "assign-cpp", tempDir, "cpp", PlagiarismConfig.defaultConfig()
        );
        PlagiarismAnalysisRequest javaRequest = new PlagiarismAnalysisRequest(
                "assign-java", tempDir, "java", PlagiarismConfig.defaultConfig()
        );

        assertEquals("cpp", cppRequest.language());
        assertEquals("java", javaRequest.language());
    }
}
