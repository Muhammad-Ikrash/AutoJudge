package autojudge.api.config;

import autojudge.MessageBroker.EvaluationProducer;
import autojudge.MessageBroker.RabbitMQConnection;
import autojudge.database.PlagiarismReportRepository;
import autojudge.database.SubmissionResultRepository;
import autojudge.reporting.ExcelGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins("http://localhost:4200")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }

}
