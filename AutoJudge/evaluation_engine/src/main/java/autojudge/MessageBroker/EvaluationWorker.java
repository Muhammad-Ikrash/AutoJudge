package autojudge.MessageBroker;

import autojudge.CoreEvaluation.grading.EvaluationEngine;
import autojudge.CoreEvaluation.model.EvaluationJob;
import autojudge.CoreEvaluation.model.SubmissionResult;
import autojudge.CoreEvaluation.model.Verdict;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Long-running RabbitMQ consumer for evaluation jobs.
 * Executes EvaluationEngine, publishes SubmissionResult, and manually ACKs messages upon success.
 * On failure: publishes SubmissionResult with INTERNAL_ERROR verdict and performs basicNack(false, false) without requeue.
 */
public class EvaluationWorker {

    private static final Logger log = LoggerFactory.getLogger(EvaluationWorker.class);

    private final RabbitMQConnection rabbitMQConnection;
    private final EvaluationEngine evaluationEngine;
    private final ResultPublisher resultPublisher;
    private final ObjectMapper objectMapper;

    private volatile Channel activeChannel;
    private volatile String consumerTag;

    public EvaluationWorker(
            RabbitMQConnection rabbitMQConnection,
            EvaluationEngine evaluationEngine,
            ResultPublisher resultPublisher
    ) {
        this.rabbitMQConnection = Objects.requireNonNull(rabbitMQConnection, "rabbitMQConnection must not be null");
        this.evaluationEngine = Objects.requireNonNull(evaluationEngine, "evaluationEngine must not be null");
        this.resultPublisher = Objects.requireNonNull(resultPublisher, "resultPublisher must not be null");
        this.objectMapper = new ObjectMapper();
    }

    public void startListening() throws IOException {
        try {
            Channel channel = rabbitMQConnection.createChannel();
            channel.basicQos(1);

            log.info("EvaluationWorker started. Listening on queue '{}'...", RabbitMQConnection.EVALUATION_QUEUE);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                long deliveryTag = delivery.getEnvelope().getDeliveryTag();
                String messageJson = new String(delivery.getBody(), StandardCharsets.UTF_8);

                EvaluationJob job = null;
                try {
                    log.info("Received evaluation message tag={}", deliveryTag);
                    job = objectMapper.readValue(messageJson, EvaluationJob.class);

                    SubmissionResult result = evaluationEngine.evaluate(job);
                    resultPublisher.publish(result);

                    channel.basicAck(deliveryTag, false);
                    log.info("Successfully processed and ACKed job tag={}", deliveryTag);
                } catch (Exception e) {
                    log.error("Error evaluating job tag={}. Publishing INTERNAL_ERROR result and NACKing without requeue.", deliveryTag, e);
                    handleFailure(channel, deliveryTag, job, e);
                }
            };

            this.consumerTag = channel.basicConsume(RabbitMQConnection.EVALUATION_QUEUE, false, deliverCallback, tag -> {
                log.info("Consumer cancelled: {}", tag);
            });
            this.activeChannel = channel;

        } catch (Exception e) {
            log.error("Failed to start EvaluationWorker listener", e);
            throw new IOException("Failed to start EvaluationWorker", e);
        }
    }

    /**
     * Gracefully stops this worker. The in-flight job (if any) completes naturally;
     * no new deliveries will arrive after this returns.
     */
    public void stop() {
        try {
            if (activeChannel != null && activeChannel.isOpen()) {
                log.info("Stopping EvaluationWorker — cancelling consumer '{}' and closing channel.", consumerTag);
                if (consumerTag != null && !consumerTag.isBlank()) {
                    activeChannel.basicCancel(consumerTag);
                }
                activeChannel.close();
            }
        } catch (Exception e) {
            log.warn("Error while stopping EvaluationWorker channel", e);
        }
    }

    public boolean isRunning() {
        return activeChannel != null && activeChannel.isOpen();
    }

    private void handleFailure(Channel channel, long deliveryTag, EvaluationJob job, Exception cause) {
        try {
            String submissionId = job != null ? job.submissionId() : "unknown-submission";
            String assignmentId = job != null ? job.assignmentId() : "unknown-assignment";
            String studentId = job != null ? job.studentId() : "unknown-student";
            String batchId = job != null ? job.batchId() : "failed-batch";
            int totalCount = job != null ? job.totalSubmissionsInBatch() : 1;

            SubmissionResult errorResult = new SubmissionResult(
                    submissionId,
                    assignmentId,
                    studentId,
                    0.0,
                    Verdict.INTERNAL_ERROR,
                    0,
                    0,
                    List.of(),
                    batchId,
                    totalCount
            );

            resultPublisher.publish(errorResult);
            channel.basicNack(deliveryTag, false, false); // NACK without requeue
            log.warn("NACKed message tag={} without requeue and published INTERNAL_ERROR result.", deliveryTag);
        } catch (Exception ex) {
            log.error("Critical failure handling NACK for tag={}", deliveryTag, ex);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (IOException ioEx) {
                log.error("Failed to send NACK for tag={}", deliveryTag, ioEx);
            }
        }
    }
}
