package autojudge.MessageBroker;

import autojudge.CoreEvaluation.grading.EvaluationEngine;
import autojudge.CoreEvaluation.model.EvaluationJob;
import autojudge.CoreEvaluation.model.SubmissionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Long-running RabbitMQ consumer that listens for EvaluationJobs, delegates execution to EvaluationEngine,
 * publishes SubmissionResult via ResultPublisher, and manages manual message acknowledgements.
 */
public class EvaluationWorker {

    private static final Logger log = LoggerFactory.getLogger(EvaluationWorker.class);

    private final RabbitMQConnection rabbitMQConnection;
    private final EvaluationEngine evaluationEngine;
    private final ResultPublisher resultPublisher;
    private final ObjectMapper objectMapper;

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

            log.info("EvaluationWorker started. Waiting for jobs on queue '{}'...", RabbitMQConnection.EVALUATION_QUEUE);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                long deliveryTag = delivery.getEnvelope().getDeliveryTag();
                String messageJson = new String(delivery.getBody(), StandardCharsets.UTF_8);

                try {
                    log.info("Received evaluation message (tag={}): {}", deliveryTag, messageJson);
                    EvaluationJob job = objectMapper.readValue(messageJson, EvaluationJob.class);

                    SubmissionResult result = evaluationEngine.evaluate(job);
                    resultPublisher.publish(result);

                    channel.basicAck(deliveryTag, false);
                    log.info("Successfully processed and acknowledged evaluation job tag={}", deliveryTag);
                } catch (Exception e) {
                    log.error("Error processing evaluation job tag={}", deliveryTag, e);
                    channel.basicNack(deliveryTag, false, false);
                }
            };

            channel.basicConsume(RabbitMQConnection.EVALUATION_QUEUE, false, deliverCallback, consumerTag -> {
                log.info("Consumer cancelled: {}", consumerTag);
            });

        } catch (Exception e) {
            log.error("Failed to start EvaluationWorker listener", e);
            throw new IOException("Failed to start EvaluationWorker", e);
        }
    }
}
