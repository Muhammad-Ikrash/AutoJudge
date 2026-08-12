package autojudge.MessageBroker;

import autojudge.CoreEvaluation.model.SubmissionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * Publishes SubmissionResult objects to the RabbitMQ result exchange.
 */
public class ResultPublisher {

    private static final Logger log = LoggerFactory.getLogger(ResultPublisher.class);

    private final RabbitMQConnection rabbitMQConnection;
    private final ObjectMapper objectMapper;

    public ResultPublisher(RabbitMQConnection rabbitMQConnection) {
        this.rabbitMQConnection = Objects.requireNonNull(rabbitMQConnection, "rabbitMQConnection must not be null");
        this.objectMapper = new ObjectMapper();
    }

    public void publish(SubmissionResult result) throws IOException {
        log.info("Publishing SubmissionResult for submissionId={}, studentId={}, verdict={}",
                result.submissionId(), result.studentId(), result.verdict());

        try (Channel channel = rabbitMQConnection.createChannel()) {
            byte[] messageBytes = objectMapper.writeValueAsBytes(result);
            channel.basicPublish(
                    RabbitMQConnection.RESULT_EXCHANGE,
                    RabbitMQConnection.RESULT_ROUTING_KEY,
                    MessageProperties.PERSISTENT_TEXT_PLAIN,
                    messageBytes
            );
            log.info("Successfully published SubmissionResult to exchange '{}'", RabbitMQConnection.RESULT_EXCHANGE);
        } catch (Exception e) {
            log.error("Failed to publish SubmissionResult for submissionId={}", result.submissionId(), e);
            throw new IOException("Failed to publish SubmissionResult", e);
        }
    }
}
