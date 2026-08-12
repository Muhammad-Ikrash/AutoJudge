package autojudge.MessageBroker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvaluationProducerTest {

    @TempDir
    Path tempDir;

    @Test
    void testDiscoversSubmissionsInDirectory() throws Exception {
        Path submissionsDir = tempDir.resolve("submissions");
        Path student1 = submissionsDir.resolve("student1");
        Path student2 = submissionsDir.resolve("student2");
        Files.createDirectories(student1);
        Files.createDirectories(student2);

        EvaluationProducer producer = new EvaluationProducer(new RabbitMQConnection("localhost", 5672, "guest", "guest", "/"));
        List<Path> discovered = producer.discoverSubmissions(tempDir);

        assertEquals(2, discovered.size());
        assertEquals("student1", discovered.get(0).getFileName().toString());
        assertEquals("student2", discovered.get(1).getFileName().toString());
    }
}
