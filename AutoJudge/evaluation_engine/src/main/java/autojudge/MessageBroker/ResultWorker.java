package autojudge.MessageBroker;

import autojudge.CoreEvaluation.model.SubmissionResult;
import autojudge.reporting.ExcelGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
    private final ExcelGenerator excelGenerator;
    private final ObjectMapper objectMapper;
    private final Map<String, List<SubmissionResult>> batchResultsMap = new ConcurrentHashMap<>();
    private final autojudge.database.SubmissionResultRepository resultRepository = new autojudge.database.SubmissionResultRepository();

    public ResultWorker(RabbitMQConnection rabbitMQConnection, ExcelGenerator excelGenerator) {
        this.rabbitMQConnection = Objects.requireNonNull(rabbitMQConnection, "rabbitMQConnection must not be null");
        this.excelGenerator = Objects.requireNonNull(excelGenerator, "excelGenerator must not be null");
        this.objectMapper = new ObjectMapper();
    }

    public List<SubmissionResult> getCollectedResults(String batchId) {
        List<SubmissionResult> results = batchResultsMap.get(batchId);
        return results != null ? new ArrayList<>(results) : Collections.emptyList();
    }

    public void startListening(Path defaultReportPath, int fallbackExpectedCount) throws IOException {
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
                    int expectedCount = result.totalSubmissionsInBatch() > 0 ? result.totalSubmissionsInBatch() : fallbackExpectedCount;

                    log.info("ResultWorker received SubmissionResult for studentId={}, verdict={}, batchId={}",
                            result.studentId(), result.verdict(), batchId);

                    List<SubmissionResult> batchList = batchResultsMap.computeIfAbsent(batchId, k -> Collections.synchronizedList(new ArrayList<>()));
                    batchList.add(result);
                    
                    // Persist the result to H2 database
                    resultRepository.save(result);
                    
                    channel.basicAck(deliveryTag, false);

                    if (expectedCount > 0 && batchList.size() >= expectedCount) {
                        log.info("Batch '{}' complete ({}/{} results). Generating Excel report at {}",
                                batchId, batchList.size(), expectedCount, defaultReportPath);

                        excelGenerator.generateReport(new ArrayList<>(batchList), defaultReportPath);
                    }
                } catch (Exception e) {
                    log.error("Failed to process SubmissionResult tag={}", deliveryTag, e);
                    channel.basicNack(deliveryTag, false, false);
                }
            };

            channel.basicConsume(RabbitMQConnection.RESULT_QUEUE, false, deliverCallback, consumerTag -> {
                log.info("ResultWorker consumer cancelled: {}", consumerTag);
            });

        } catch (Exception e) {
            log.error("Failed to start ResultWorker listener", e);
            throw new IOException("Failed to start ResultWorker", e);
        }
    }
}
