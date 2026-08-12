package autojudge.grading;

import autojudge.docker.DockerRunner;
import autojudge.loader.SubmissionLoader;
import autojudge.model.EvaluationContext;
import autojudge.model.ExecutionResult;
import autojudge.model.Submission;
import autojudge.model.SubmissionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates evaluation across multiple student submissions.
 */
public class GradingOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GradingOrchestrator.class);

    private final DockerRunner dockerRunner;
    private final GradingService gradingService;

    public GradingOrchestrator() {
        this(new DockerRunner(), new GradingService());
    }

    public GradingOrchestrator(DockerRunner dockerRunner, GradingService gradingService) {
        this.dockerRunner = Objects.requireNonNull(dockerRunner, "dockerRunner must not be null");
        this.gradingService = Objects.requireNonNull(gradingService, "gradingService must not be null");
    }

    // public List<SubmissionResult> evaluateAllSubmissions(EvaluationContext context) throws Exception {
    //     List<SubmissionResult> results = new ArrayList<>();
    //     log.info("Starting evaluation for {} submission folder(s)", context.submissionFolders().size());

    //     for (Path submissionFolder : context.submissionFolders()) {
    //         SubmissionResult result = evaluateSingleSubmission(context, submissionFolder);
    //         results.add(result);
    //     }

    //     return results;
    // }

    // public SubmissionResult evaluateSingleSubmission(EvaluationContext context, Path submissionFolder) throws Exception {
    //     String studentId = resolveStudentId(submissionFolder);
    //     log.info("Evaluating submission for student '{}' at {}", studentId, submissionFolder);

    //     Submission submission = SubmissionLoader.load(
    //             submissionFolder,
    //             context.inputDirectory(),
    //             context.outputDirectory(),
    //             studentId,
    //             context.assignment().assignmentId()
    //     );

    //     List<ExecutionResult> executionResults = dockerRunner.runSubmission(
    //             context.containerConfig(), submission, context.testCases()
    //     );

    //     return gradingService.grade(submission, context.testCases(), executionResults);
    // }

        public SubmissionResult evaluate(EvaluationContext context) throws Exception {
        String studentId = resolveStudentId(context.submissionPath());
        log.info("Evaluating submission for student '{}' at {}", studentId, context.submissionPath());

        Submission submission = SubmissionLoader.load(
                context.submissionPath(),
                context.inputDirectory(),
                context.outputDirectory(),
                studentId,
                context.assignment().assignmentId()
        );

        List<ExecutionResult> executionResults = dockerRunner.runSubmission(
                context.containerConfig(), submission, context.testCases()
        );

        return gradingService.grade(submission, context.testCases(), executionResults);
    }



    private String resolveStudentId(Path submissionFolder) {
        if (submissionFolder == null || submissionFolder.getFileName() == null) {
            return "";
        }
        return submissionFolder.getFileName().toString();
    }
}
