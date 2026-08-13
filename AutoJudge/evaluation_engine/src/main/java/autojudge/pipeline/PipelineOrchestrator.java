package autojudge.pipeline;

import autojudge.PlagiarismDetection.model.PlagiarismAnalysisRequest;
import autojudge.PlagiarismDetection.model.PlagiarismConfig;
import autojudge.PlagiarismDetection.model.PlagiarismReport;
import autojudge.PlagiarismDetection.service.PlagiarismAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Top-level pipeline orchestrator driving optional execution stages (such as plagiarism detection).
 */
public class PipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PipelineOrchestrator.class);

    private final PlagiarismAnalysisService plagiarismAnalysisService;

    public PipelineOrchestrator(PlagiarismAnalysisService plagiarismAnalysisService) {
        this.plagiarismAnalysisService = Objects.requireNonNull(plagiarismAnalysisService, "plagiarismAnalysisService must not be null");
    }

    public PipelineOrchestrator() {
        this(new PlagiarismAnalysisService());
    }

    /**
     * Executes the optional plagiarism pipeline stage if enabled by the request.
     *
     * @param assignmentId Identifier of the assignment.
     * @param assignmentPath Path to the assignment folder containing student submissions.
     * @param plagiarismEnabled Toggle flag from request/frontend.
     * @return Optional containing PlagiarismReport if plagiarism checking was enabled, empty otherwise.
     */
    public Optional<PlagiarismReport> processPlagiarismStage(
            String assignmentId,
            Path assignmentPath,
            boolean plagiarismEnabled
    ) {
        if (!plagiarismEnabled) {
            log.info("Plagiarism checking is disabled for assignment '{}'. Skipping plagiarism pipeline stage.", assignmentId);
            return Optional.empty();
        }

        log.info("Plagiarism checking is enabled for assignment '{}'. Triggering plagiarism analysis service.", assignmentId);
        PlagiarismAnalysisRequest request = new PlagiarismAnalysisRequest(
                assignmentId,
                assignmentPath,
                PlagiarismConfig.defaultConfig()
        );

        PlagiarismReport report = plagiarismAnalysisService.analyze(request);
        return Optional.of(report);
    }
}
