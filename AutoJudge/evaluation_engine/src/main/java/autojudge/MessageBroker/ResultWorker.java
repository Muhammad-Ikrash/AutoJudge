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
import java.util.Objects;

/**
 * Long-running RabbitMQ consumer that listens for SubmissionResults, collects them,
 * and delegates Excel report generation to ExcelGenerator.
 */
public class ResultWorker {

    private static final Logger log = LoggerFactory.getLogger(ResultWorker.class);

    private final RabbitMQConnection rabbitMQConnection;
    private final ExcelGenerator excelGenerator;
    private final ObjectMapper objectMapper;
    private final List<SubmissionResult> collectedResults = Collections.synchronizedList(new ArrayList<>());

    public ResultWorker(RabbitMQConnection rabbitMQConnection, ExcelGenerator excelGenerator) {
        this.rabbitMQConnection = Objects.requireNonNull(rabbitMQConnection, "rabbitMQConnection must not be null");
        this.excelGenerator = Objects.requireNonNull(excelGenerator, "excelGenerator must not be null");
        this.objectMapper = new ObjectMapper();
    }

    public List<SubmissionResult> getCollectedResults() {
        return new ArrayList<>(collectedResults);
    }

    public void startListening(Path reportOutputPath, int expectedResultCount) throws IOException {
        try {
            Channel channel = rabbitMQConnection.createChannel();
            channel.basicQos(1);

            log.info("ResultWorker started. Listening on queue '{}' (expected results: {})...",
                    RabbitMQConnection.RESULT_QUEUE, expectedResultCount);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                long deliveryTag = delivery.getEnvelope().getDeliveryTag();
                String messageJson = new String(delivery.getBody(), StandardCharsets.UTF_8);

                try {
                    SubmissionResult result = objectMapper.readValue(messageJson, SubmissionResult.class);
                    log.info("ResultWorker received SubmissionResult: studentId={}, verdict={}, score={}",
                            result.studentId(), result.verdict(), result.score());

                    collectedResults.add(result);
                    channel.basicAck(deliveryTag, false);

                    if (expectedResultCount > 0 && collectedResults.size() >= expectedResultCount) {
                        log.info("Collected {}/{} expected results. Generating Excel report at {}",
                                collectedResults.size(), expectedResultCount, reportOutputPath);
                        excelGenerator.generateReport(new ArrayList<>(collectedResults), reportOutputPath);
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
