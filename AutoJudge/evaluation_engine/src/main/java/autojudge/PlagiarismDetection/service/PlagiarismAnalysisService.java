package autojudge.PlagiarismDetection.service;

import autojudge.PlagiarismDetection.detector.JPlagDetector;
import autojudge.PlagiarismDetection.detector.PlagiarismDetector;
import autojudge.PlagiarismDetection.model.PlagiarismAnalysisRequest;
import autojudge.PlagiarismDetection.model.PlagiarismReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Application-level orchestration service for plagiarism analysis.
 * Pipeline and orchestrator components invoke this service rather than calling detector adapters directly.
 */
public class PlagiarismAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(PlagiarismAnalysisService.class);

    private final PlagiarismDetector detector;

    public PlagiarismAnalysisService(PlagiarismDetector detector) {
        this.detector = Objects.requireNonNull(detector, "detector must not be null");
    }

    public PlagiarismAnalysisService() {
        this(new JPlagDetector());
    }

    /**
     * Triggers plagiarism analysis for the provided request.
     *
     * @param request Analysis parameters including assignment ID and filesystem path.
     * @return Aggregated PlagiarismReport containing pairwise similarity scores.
     */
    public PlagiarismReport analyze(PlagiarismAnalysisRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        log.info("Executing PlagiarismAnalysisService for assignment: {}", request.assignmentId());
        return detector.analyze(request);
    }
}
