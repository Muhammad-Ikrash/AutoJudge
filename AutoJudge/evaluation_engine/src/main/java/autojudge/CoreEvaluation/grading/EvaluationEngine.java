package autojudge.CoreEvaluation.grading;

import autojudge.CoreEvaluation.config.ContainerConfig;
import autojudge.CoreEvaluation.loader.AssignmentLoader;
import autojudge.CoreEvaluation.loader.TestCaseFileProcessor;
import autojudge.CoreEvaluation.loader.WeightsFileParser;
import autojudge.CoreEvaluation.model.Assignment;
import autojudge.CoreEvaluation.model.EvaluationContext;
import autojudge.CoreEvaluation.model.EvaluationJob;
import autojudge.CoreEvaluation.model.SubmissionResult;
import autojudge.CoreEvaluation.model.TestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure boundary class between RabbitMQ workers and the core evaluator.
 * Converts an EvaluationJob into an EvaluationContext, executes grading via GradingOrchestrator,
 * and returns the SubmissionResult. Zero RabbitMQ awareness.
 */
public class EvaluationEngine {

    private static final Logger log = LoggerFactory.getLogger(EvaluationEngine.class);

    private final GradingOrchestrator gradingOrchestrator;
    private final WeightsFileParser weightsFileParser;
    private final TestCaseFileProcessor testCaseFileProcessor;

    public EvaluationEngine() {
        this(new GradingOrchestrator(), new WeightsFileParser(), new TestCaseFileProcessor());
    }

    public EvaluationEngine(
            GradingOrchestrator gradingOrchestrator,
            WeightsFileParser weightsFileParser,
            TestCaseFileProcessor testCaseFileProcessor
    ) {
        this.gradingOrchestrator = Objects.requireNonNull(gradingOrchestrator, "gradingOrchestrator must not be null");
        this.weightsFileParser = Objects.requireNonNull(weightsFileParser, "weightsFileParser must not be null");
        this.testCaseFileProcessor = Objects.requireNonNull(testCaseFileProcessor, "testCaseFileProcessor must not be null");
    }

    public SubmissionResult evaluate(EvaluationJob job) throws Exception {
        log.info("EvaluationEngine processing job: submissionId={}, studentId={}, batchId={}",
                job.submissionId(), job.studentId(), job.batchId());

        Path assignmentPath = Path.of(job.assignmentPath());
        Path submissionPath = Path.of(job.submissionPath());

        Path inputDirectory = assignmentPath.resolve("input");
        Path outputDirectory = assignmentPath.resolve("expected");
        Path configFile = assignmentPath.resolve("config.json");
        Path weightsFile = assignmentPath.resolve("weights.json");

        if (!Files.exists(configFile)) {
            throw new IllegalArgumentException("Assignment config file not found at " + configFile.toAbsolutePath());
        }
        if (!Files.exists(weightsFile)) {
            throw new IllegalArgumentException("Assignment weights file not found at " + weightsFile.toAbsolutePath());
        }

        Assignment assignment = AssignmentLoader.loadConfig(configFile);
        Map<String, Integer> weightsByTestCase = weightsFileParser.parse(weightsFile);
        List<TestCase> testCases = testCaseFileProcessor.processTestCases(inputDirectory, outputDirectory, weightsByTestCase);
        ContainerConfig containerConfig = ContainerConfig.from(assignment);

        EvaluationContext context = new EvaluationContext(
                submissionPath,
                inputDirectory,
                outputDirectory,
                assignment,
                containerConfig,
                testCases
        );

        SubmissionResult rawResult = gradingOrchestrator.evaluate(context);

        // Attach batch metadata to SubmissionResult
        return new SubmissionResult(
                rawResult.submissionId(),
                rawResult.assignmentId(),
                rawResult.studentId(),
                rawResult.score(),
                rawResult.verdict(),
                rawResult.passedTests(),
                rawResult.totalTests(),
                rawResult.testCasesResults(),
                job.batchId(),
                job.totalSubmissionsInBatch()
        );
    }
}
