package autojudge.api.config;

import autojudge.MessageBroker.EvaluationProducer;
import autojudge.MessageBroker.RabbitMQConnection;
import autojudge.database.PlagiarismReportRepository;
import autojudge.database.SubmissionResultRepository;
import autojudge.reporting.ExcelGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public RabbitMQConnection rabbitMQConnection() {
        return new RabbitMQConnection();
    }

    @Bean
    public EvaluationProducer evaluationProducer(RabbitMQConnection rabbitMQConnection) {
        return new EvaluationProducer(rabbitMQConnection);
    }

    @Bean
    public SubmissionResultRepository submissionResultRepository() {
        return new SubmissionResultRepository();
    }

    @Bean
    public PlagiarismReportRepository plagiarismReportRepository() {
        return new PlagiarismReportRepository();
    }

    @Bean
    public ExcelGenerator excelGenerator() {
        return new ExcelGenerator();
    }
}
