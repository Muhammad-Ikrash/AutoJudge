package autojudge.PlagiarismDetection.detector;

import autojudge.PlagiarismDetection.model.PlagiarismAnalysisRequest;
import autojudge.PlagiarismDetection.model.PlagiarismReport;

/**
 * Extensible abstraction boundary for plagiarism detectors (e.g. JPlag, MOSS, etc.).
 * Application and pipeline components depend solely on this interface.
 */
public interface PlagiarismDetector {

    /**
     * Executes plagiarism analysis for the given request and returns a domain report.
     *
     * @param request Analysis request containing assignment path and configuration.
     * @return Aggregated domain report containing similarity pairs.
     */
    PlagiarismReport analyze(PlagiarismAnalysisRequest request);
}
