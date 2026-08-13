package autojudge.pipeline;

import autojudge.PlagiarismDetection.model.PlagiarismAnalysisRequest;
import autojudge.PlagiarismDetection.model.PlagiarismReport;
import autojudge.PlagiarismDetection.model.SimilarityPair;
import autojudge.PlagiarismDetection.service.PlagiarismAnalysisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PipelineOrchestratorTest {

    @Test
    void testPlagiarismDisabled_skipsPlagiarismExecution(@TempDir Path tempDir) {
        AtomicBoolean called = new AtomicBoolean(false);
        PlagiarismAnalysisService serviceMock = new PlagiarismAnalysisService() {
            @Override
            public PlagiarismReport analyze(PlagiarismAnalysisRequest request) {
                called.set(true);
                return new PlagiarismReport(request.assignmentId(), List.of());
            }
        };

        PipelineOrchestrator orchestrator = new PipelineOrchestrator(serviceMock);
        Optional<PlagiarismReport> result = orchestrator.processPlagiarismStage("assignment-1", tempDir, false);

        assertTrue(result.isEmpty(), "Expected empty report when plagiarism is disabled");
        assertFalse(called.get(), "Expected PlagiarismAnalysisService NOT to be called when disabled");
    }

    @Test
    void testPlagiarismEnabled_invokesPlagiarismExecution(@TempDir Path tempDir) {
        AtomicBoolean called = new AtomicBoolean(false);
        PlagiarismAnalysisService serviceMock = new PlagiarismAnalysisService() {
            @Override
            public PlagiarismReport analyze(PlagiarismAnalysisRequest request) {
                called.set(true);
                return new PlagiarismReport(request.assignmentId(), List.of(new SimilarityPair("studentA", "studentB", 95.0)));
            }
        };

        PipelineOrchestrator orchestrator = new PipelineOrchestrator(serviceMock);
        Optional<PlagiarismReport> result = orchestrator.processPlagiarismStage("assignment-1", tempDir, true);

        assertTrue(result.isPresent(), "Expected PlagiarismReport when plagiarism is enabled");
        assertTrue(called.get(), "Expected PlagiarismAnalysisService to be called when enabled");
        assertEquals("assignment-1", result.get().assignmentId());
        assertEquals(1, result.get().similarities().size());
        assertEquals("studentA", result.get().similarities().get(0).submissionA());
    }

    @Test
    void testFullPipelineIntegration_withSimilarSubmissions(@TempDir Path tempDir) throws IOException {
        Path submissions = tempDir.resolve("submissions");
        Path studentA = submissions.resolve("studentA");
        Path studentB = submissions.resolve("studentB");
        Files.createDirectories(studentA);
        Files.createDirectories(studentB);

        String codeA = """
                #include <iostream>
                #include <vector>
                
                int calculateSum(const std::vector<int>& numbers) {
                    int total = 0;
                    for (int n : numbers) {
                        total += n;
                    }
                    return total;
                }
                
                int main() {
                    std::vector<int> vals = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
                    int res = calculateSum(vals);
                    std::cout << "Sum: " << res << std::endl;
                    return 0;
                }
                """;

        String codeB = """
                #include <iostream>
                #include <vector>
                
                int computeTotal(const std::vector<int>& arr) {
                    int sumVal = 0;
                    for (int x : arr) {
                        sumVal += x;
                    }
                    return sumVal;
                }
                
                int main() {
                    std::vector<int> data = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
                    int ans = computeTotal(data);
                    std::cout << "Sum: " << ans << std::endl;
                    return 0;
                }
                """;

        Files.writeString(studentA.resolve("main.cpp"), codeA);
        Files.writeString(studentB.resolve("main.cpp"), codeB);

        PipelineOrchestrator orchestrator = new PipelineOrchestrator();
        Optional<PlagiarismReport> result = orchestrator.processPlagiarismStage("assignment-demo", tempDir, true);

        assertTrue(result.isPresent());
        assertEquals("assignment-demo", result.get().assignmentId());
        assertFalse(result.get().similarities().isEmpty(), "Expected JPlag to find similarity between studentA and studentB");
    }
}
