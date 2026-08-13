package autojudge.PlagiarismDetection.detector;

import autojudge.PlagiarismDetection.model.PlagiarismAnalysisRequest;
import autojudge.PlagiarismDetection.model.PlagiarismReport;
import autojudge.PlagiarismDetection.model.SimilarityPair;
import de.jplag.JPlag;
import de.jplag.JPlagResult;
import de.jplag.Language;
import de.jplag.cpp.CPPLanguage;
import de.jplag.java.JavaLanguage;
import de.jplag.options.JPlagOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Adapter encapsulating the JPlag plagiarism detection engine.
 * All JPlag-specific imports and result object mappings are contained within this class.
 */
public class JPlagDetector implements PlagiarismDetector {

    private static final Logger log = LoggerFactory.getLogger(JPlagDetector.class);

    @Override
    public PlagiarismReport analyze(PlagiarismAnalysisRequest request) {
        log.info("Starting JPlag analysis for assignment: {} (language: {})", request.assignmentId(), request.language());

        Path submissionsDir = request.assignmentPath().resolve("submissions");
        Path rootToScan = Files.isDirectory(submissionsDir) ? submissionsDir : request.assignmentPath();

        Language jplagLanguage = resolveJPlagLanguage(request.language());

        try {
            JPlagOptions options = new JPlagOptions(
                    jplagLanguage,
                    Set.of(rootToScan.toFile()),
                    Collections.emptySet()
            );

            JPlag jplag = new JPlag(options);
            JPlagResult result = jplag.run();

            List<SimilarityPair> pairs = new ArrayList<>();
            if (result != null) {
                var comparisons = result.getComparisons(0);
                if (comparisons != null) {
                    comparisons.forEach(comparison -> {
                        String subA = comparison.firstSubmission() != null ? comparison.firstSubmission().getName() : "unknown-A";
                        String subB = comparison.secondSubmission() != null ? comparison.secondSubmission().getName() : "unknown-B";
                        double similarity = comparison.similarity();

                        pairs.add(new SimilarityPair(subA, subB, similarity));
                    });
                }
            }

            log.info("Completed JPlag analysis for assignment {}. Found {} similarity pair(s)",
                    request.assignmentId(), pairs.size());

            return new PlagiarismReport(request.assignmentId(), pairs);
        } catch (Exception e) {
            log.error("JPlag execution failed for assignment {}", request.assignmentId(), e);
            throw new RuntimeException("JPlag plagiarism analysis failed: " + e.getMessage(), e);
        }
    }

    private Language resolveJPlagLanguage(String language) {
        if (language == null) {
            throw new UnsupportedOperationException("Plagiarism detection is not supported for null language");
        }
        return switch (language.toLowerCase().trim()) {
            case "cpp", "c++" -> new CPPLanguage();
            case "java" -> new JavaLanguage();
            default -> throw new UnsupportedOperationException(
                    "Plagiarism detection is not supported for language: " + language);
        };
    }
}
