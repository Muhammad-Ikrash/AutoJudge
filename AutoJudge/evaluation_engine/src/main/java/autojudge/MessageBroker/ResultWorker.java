package autojudge.MessageBroker;

import autojudge.CoreEvaluation.model.SubmissionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Long-running RabbitMQ consumer that collects SubmissionResults grouped by batchId.
 * Triggers Excel report generation once received count == expected count for a batch.
 */
public class ResultWorker {

    private static final Logger log = LoggerFactory.getLogger(ResultWorker.class);

    private final RabbitMQConnection rabbitMQConnection;
    private final ObjectMapper objectMapper;
    private final Map<String, List<SubmissionResult>> batchResultsMap = new ConcurrentHashMap<>();
    private final autojudge.database.SubmissionResultRepository resultRepository = new autojudge.database.SubmissionResultRepository();
    private final Map<String, String> assignmentToLatestBatchMap = new ConcurrentHashMap<>();

    private volatile com.rabbitmq.client.Channel activeChannel;
    private volatile boolean stopped = false;
    private volatile String latestBatchId = "—";

    public String getLatestBatchId() {
        return latestBatchId;
    }

    public ResultWorker(RabbitMQConnection rabbitMQConnection) {
        this.rabbitMQConnection = Objects.requireNonNull(rabbitMQConnection, "rabbitMQConnection must not be null");
        this.objectMapper = new ObjectMapper();
    }

    public List<SubmissionResult> getCollectedResults(String batchId) {
        List<SubmissionResult> results = batchResultsMap.get(batchId);
        return results != null ? new ArrayList<>(results) : Collections.emptyList();
    }

    public String getLatestBatchIdForAssignment(String assignmentId) {
        return assignmentToLatestBatchMap.get(assignmentId);
    }

    public Map<String, Object> getBatchStatus(String batchId) {
        if (batchId == null) return Map.of("completed", 0, "total", 0);
        List<SubmissionResult> results = batchResultsMap.get(batchId);
        if (results == null || results.isEmpty()) return Map.of("completed", 0, "total", 0);
        int expectedCount = results.get(0).totalSubmissionsInBatch();
        return Map.of("completed", results.size(), "total", expectedCount);
    }

    public void startListening() throws IOException {
        try {
            Channel channel = rabbitMQConnection.createChannel();
            channel.basicQos(1);

            log.info("ResultWorker started. Listening on queue '{}'...", RabbitMQConnection.RESULT_QUEUE);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                long deliveryTag = delivery.getEnvelope().getDeliveryTag();
                String messageJson = new String(delivery.getBody(), StandardCharsets.UTF_8);

                try {
                    SubmissionResult result = objectMapper.readValue(messageJson, SubmissionResult.class);
                    String batchId = result.batchId() != null ? result.batchId() : "default-batch";
                    int expectedCount = result.totalSubmissionsInBatch() > 0 ? result.totalSubmissionsInBatch() : 0;

                    log.info("ResultWorker received SubmissionResult for studentId={}, verdict={}, batchId={}",
                            result.studentId(), result.verdict(), batchId);

                    latestBatchId = batchId;

                    List<SubmissionResult> batchList = batchResultsMap.computeIfAbsent(batchId, k -> Collections.synchronizedList(new ArrayList<>()));
                    batchList.add(result);
                    if (result.assignmentId() != null) {
                        assignmentToLatestBatchMap.put(result.assignmentId(), batchId);
                    }
                    
                    // Persist the result to H2 database
                    resultRepository.save(result);
                    
                    channel.basicAck(deliveryTag, false);

                    if (expectedCount > 0 && batchList.size() >= expectedCount) {
                        log.info("Batch '{}' complete ({}/{} results).",
                                batchId, batchList.size(), expectedCount);
                    }
                } catch (Exception e) {
                    log.error("Failed to process SubmissionResult tag={}", deliveryTag, e);
                    channel.basicNack(deliveryTag, false, false);
                }
            };

            channel.basicConsume(RabbitMQConnection.RESULT_QUEUE, false, deliverCallback, tag -> {
                log.info("ResultWorker consumer cancelled: {}", tag);
            });
            this.activeChannel = channel;

        } catch (Exception e) {
            log.error("Failed to start ResultWorker listener", e);
            throw new IOException("Failed to start ResultWorker", e);
        }
    }

    /**
     * Gracefully stops the ResultWorker. In-flight message processing completes naturally;
     * no new deliveries will arrive after this returns.
     */
    public void stop() {
        stopped = true;
        try {
            if (activeChannel != null && activeChannel.isOpen()) {
                log.info("Stopping ResultWorker — closing channel.");
                activeChannel.close();
            }
        } catch (Exception e) {
            log.warn("Error while stopping ResultWorker channel", e);
        }
    }

    public boolean isRunning() {
        if (stopped) return false;
        if (activeChannel == null) return true;
        return activeChannel.isOpen();
    }
}
