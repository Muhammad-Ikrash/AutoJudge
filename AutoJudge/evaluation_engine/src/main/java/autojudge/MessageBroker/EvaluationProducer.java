package autojudge.MessageBroker;

import autojudge.CoreEvaluation.compiler.SubmissionScanner;
import autojudge.CoreEvaluation.model.EvaluationJob;
import autojudge.pipeline.PipelineOrchestrator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Discovers student submissions from an assignment root directory, generates a single batchId per run,
 * constructs EvaluationJob requests, and publishes them to the RabbitMQ evaluation exchange.
 */
public class EvaluationProducer {

    private static final Logger log = LoggerFactory.getLogger(EvaluationProducer.class);

    private final RabbitMQConnection rabbitMQConnection;
    private final ObjectMapper objectMapper;

    public EvaluationProducer(RabbitMQConnection rabbitMQConnection) {
        this.rabbitMQConnection = Objects.requireNonNull(rabbitMQConnection, "rabbitMQConnection must not be null");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Discovers submissions under assignmentPath/submissions/ and publishes EvaluationJobs to RabbitMQ.
     * Optionally runs plagiarism detection if enablePlagiarism is true.
     *
     * @param assignmentPath Path to the assignment root folder.
     * @param enablePlagiarism Toggle flag for optional plagiarism analysis.
     * @return Number of evaluation jobs published.
     * @throws IOException If discovering or publishing fails.
     */
    public int produceEvaluationJobs(Path assignmentPath, boolean enablePlagiarism) throws IOException {
        String assignmentId = assignmentPath.getFileName() != null
                ? assignmentPath.getFileName().toString()
                : "assignment-1";

        List<Path> submissionPaths = discoverSubmissions(assignmentPath);
        int totalSubmissions = submissionPaths.size();
        String batchId = "batch-" + UUID.randomUUID().toString();

        log.info("Discovered {} submission(s) for assignment at {}. Generated batchId={}",
                totalSubmissions, assignmentPath, batchId);

        // Auto-detect programming language dynamically from discovered submissions
        String detectedLanguage = detectLanguageFromSubmissions(submissionPaths);
        log.info("Auto-detected programming language for assignment '{}': {}", assignmentId, detectedLanguage);

        // Perform optional plagiarism detection if requested
        if (enablePlagiarism) {
            PipelineOrchestrator pipeline = new PipelineOrchestrator();
            pipeline.processPlagiarismStage(assignmentId, assignmentPath, detectedLanguage, true);
        }

        try (Channel channel = rabbitMQConnection.createChannel()) {
            for (Path subPath : submissionPaths) {
                String studentId = subPath.getFileName().toString();
                String submissionId = assignmentId + "-" + studentId + "-" + UUID.randomUUID().toString().substring(0, 8);

                EvaluationJob job = new EvaluationJob(
                        submissionId,
                        assignmentId,
                        studentId,
                        assignmentPath.toAbsolutePath().toString(),
                        subPath.toAbsolutePath().toString(),
                        batchId,
                        totalSubmissions
                );

                byte[] messageBytes = objectMapper.writeValueAsBytes(job);
                channel.basicPublish(
                        RabbitMQConnection.EVALUATION_EXCHANGE,
                        RabbitMQConnection.EVALUATION_ROUTING_KEY,
                        MessageProperties.PERSISTENT_TEXT_PLAIN,
                        messageBytes
                );
                log.info("Published EvaluationJob: studentId={}, submissionId={}, batchId={}",
                        studentId, submissionId, batchId);
            }
        } catch (Exception e) {
            log.error("Failed to produce evaluation jobs for {}", assignmentPath, e);
            throw new IOException("Failed to produce evaluation jobs", e);
        }

        return totalSubmissions;
    }

    public int produceEvaluationJobs(Path assignmentPath) throws IOException {
        return produceEvaluationJobs(assignmentPath, false);
    }

    private String detectLanguageFromSubmissions(List<Path> submissionPaths) {
        if (submissionPaths == null || submissionPaths.isEmpty()) {
            return "cpp";
        }
        for (Path subPath : submissionPaths) {
            try {
                var layout = SubmissionScanner.scan(subPath);
                if (layout != null && layout.language() != null) {
                    return layout.language().name().toLowerCase();
                }
            } catch (Exception ignored) {
            }
        }
        return "cpp";
    }

    /**
     * Discovers student submission directories under assignmentPath/submissions/.
     */
    public List<Path> discoverSubmissions(Path assignmentPath) throws IOException {
        List<Path> submissionFolders = new ArrayList<>();
        Path submissionsDir = assignmentPath.resolve("submissions");

        if (Files.exists(submissionsDir) && Files.isDirectory(submissionsDir)) {
            try (var stream = Files.list(submissionsDir)) {
                stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(submissionFolders::add);
            }
        }

        if (submissionFolders.isEmpty() && Files.exists(assignmentPath)) {
            submissionFolders.add(assignmentPath);
        }
        return submissionFolders;
    }
}
