package autojudge.MessageBroker;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * Centralized RabbitMQ connection manager and topology builder.
 * Configures evaluation and result queues/exchanges.
 */
public class RabbitMQConnection implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConnection.class);

    public static final String EVALUATION_EXCHANGE = "evaluation.exchange";
    public static final String EVALUATION_QUEUE = "evaluation.queue";
    public static final String EVALUATION_ROUTING_KEY = "evaluation.key";

    public static final String RESULT_EXCHANGE = "result.exchange";
    public static final String RESULT_QUEUE = "result.queue";
    public static final String RESULT_ROUTING_KEY = "result.key";

    private final ConnectionFactory factory;
    private Connection connection;

    public RabbitMQConnection() {
        this(
            getEnv("RABBITMQ_HOST", "localhost"),
            Integer.parseInt(getEnv("RABBITMQ_PORT", "5672")),
            getEnv("RABBITMQ_USERNAME", "autojudge"),
            getEnv("RABBITMQ_PASSWORD", "autojudge"),
            getEnv("RABBITMQ_VHOST", "/")
        );
    }

    public RabbitMQConnection(String host, int port, String username, String password, String vhost) {
        this.factory = new ConnectionFactory();
        this.factory.setHost(host);
        this.factory.setPort(port);
        this.factory.setUsername(username);
        this.factory.setPassword(password);
        this.factory.setVirtualHost(vhost);
    }

    public synchronized Connection getConnection() throws IOException, TimeoutException {
        if (connection == null || !connection.isOpen()) {
            log.info("Establishing RabbitMQ connection to {}:{}", factory.getHost(), factory.getPort());
            connection = factory.newConnection("AutoJudge-Connection");
            setupTopology();
        }
        return connection;
    }

    public Channel createChannel() throws IOException, TimeoutException {
        return getConnection().createChannel();
    }

    public void setupTopology() throws IOException, TimeoutException {
        try (Channel channel = connection.createChannel()) {
            log.info("Setting up RabbitMQ topology (exchanges, queues, bindings)...");

            // Evaluation Queue setup
            channel.exchangeDeclare(EVALUATION_EXCHANGE, "direct", true);
            channel.queueDeclare(EVALUATION_QUEUE, true, false, false, null);
            channel.queueBind(EVALUATION_QUEUE, EVALUATION_EXCHANGE, EVALUATION_ROUTING_KEY);

            // Result Queue setup
            channel.exchangeDeclare(RESULT_EXCHANGE, "direct", true);
            channel.queueDeclare(RESULT_QUEUE, true, false, false, null);
            channel.queueBind(RESULT_QUEUE, RESULT_EXCHANGE, RESULT_ROUTING_KEY);

            log.info("RabbitMQ topology initialized successfully.");
        }
    }

    @Override
    public synchronized void close() {
        if (connection != null && connection.isOpen()) {
            try {
                log.info("Closing RabbitMQ connection.");
                connection.close();
            } catch (IOException e) {
                log.error("Error while closing RabbitMQ connection", e);
            }
        }
    }

    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
