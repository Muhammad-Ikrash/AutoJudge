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
     * @param language Programming language of the assignment (e.g. "cpp", "java").
     * @param plagiarismEnabled Toggle flag from request/frontend.
     * @return Optional containing PlagiarismReport if plagiarism checking was enabled and supported, empty otherwise.
     */
    public Optional<PlagiarismReport> processPlagiarismStage(
            String assignmentId,
            Path assignmentPath,
            String language,
            boolean plagiarismEnabled
    ) {
        if (!plagiarismEnabled) {
            log.info("Plagiarism checking is disabled for assignment '{}'. Skipping plagiarism pipeline stage.", assignmentId);
            return Optional.empty();
        }

        log.info("Plagiarism checking is enabled for assignment '{}' (language: '{}'). Triggering plagiarism analysis service.", assignmentId, language);
        try {
            PlagiarismAnalysisRequest request = new PlagiarismAnalysisRequest(
                    assignmentId,
                    assignmentPath,
                    language,
                    PlagiarismConfig.defaultConfig()
            );

            PlagiarismReport report = plagiarismAnalysisService.analyze(request);
            return Optional.of(report);
        } catch (UnsupportedOperationException e) {
            log.warn("Plagiarism detection unavailable for language '{}', skipping plagiarism pipeline stage: {}", language, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Overloaded helper defaulting language to "cpp" for backwards compatibility.
     */
    public Optional<PlagiarismReport> processPlagiarismStage(
            String assignmentId,
            Path assignmentPath,
            boolean plagiarismEnabled
    ) {
        return processPlagiarismStage(assignmentId, assignmentPath, "cpp", plagiarismEnabled);
    }
}
