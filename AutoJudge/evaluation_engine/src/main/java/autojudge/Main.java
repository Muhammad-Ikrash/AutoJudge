package autojudge;

import autojudge.CoreEvaluation.grading.EvaluationEngine;
import autojudge.CoreEvaluation.loader.CommandLineParser;
import autojudge.CoreEvaluation.model.EvaluationContext;
import autojudge.CoreEvaluation.model.SubmissionResult;
import autojudge.MessageBroker.EvaluationProducer;
import autojudge.MessageBroker.EvaluationWorker;
import autojudge.MessageBroker.RabbitMQConnection;
import autojudge.MessageBroker.ResultPublisher;
import autojudge.MessageBroker.ResultWorker;
import autojudge.reporting.ExcelGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Main CLI entry point for AutoJudge matching the RabbitMQ specification.
 *
 * Supported commands:
 *   java -jar autojudge.jar producer <assignment-path>
 *   java -jar autojudge.jar worker
 *   java -jar autojudge.jar result-worker [reportOutputPath] [expectedCount]
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) {
        if (args == null || args.length == 0) {
            printUsage();
            System.exit(1);
        }

        String command = args[0].toLowerCase();
        try {
            switch (command) {
                case "producer":
                    if (args.length < 2) {
                        System.err.println("Usage: java -jar autojudge.jar producer <assignment-path>");
                        System.exit(1);
                    }
                    produceJobs(Path.of(args[1]));
                    break;
                case "worker":
                case "evaluator":
                    startEvaluationWorker();
                    break;
                case "result-worker":
                    startResultWorker(args);
                    break;
                default:
                    evaluateFromCmd(args);
                    break;
            }
        } catch (Exception e) {
            log.error("Execution failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    public static void produceJobs(Path assignmentPath) throws Exception {
        log.info("Starting Producer mode for assignment path: {}", assignmentPath);
        RabbitMQConnection rabbitMQConnection = new RabbitMQConnection();
        EvaluationProducer producer = new EvaluationProducer(rabbitMQConnection);

        int count = producer.produceEvaluationJobs(assignmentPath);
        log.info("Successfully produced {} evaluation job(s) for assignment '{}'", count, assignmentPath);
    }

    public static void startEvaluationWorker() throws Exception {
        log.info("Starting AutoJudge Evaluation Worker mode...");
        RabbitMQConnection rabbitMQConnection = new RabbitMQConnection();
        EvaluationEngine evaluationEngine = new EvaluationEngine();
        ResultPublisher resultPublisher = new ResultPublisher(rabbitMQConnection);

        EvaluationWorker worker = new EvaluationWorker(rabbitMQConnection, evaluationEngine, resultPublisher);
        worker.startListening();
    }

    public static void startResultWorker(String[] args) throws Exception {
        Path reportOutputPath = Path.of(args.length > 1 ? args[1] : "results.xlsx");
        int expectedCount = args.length > 2 ? Integer.parseInt(args[2]) : 0;

        log.info("Starting AutoJudge Result Worker mode (Output: {}, Expected Count: {})...", reportOutputPath, expectedCount);
        RabbitMQConnection rabbitMQConnection = new RabbitMQConnection();
        ExcelGenerator excelGenerator = new ExcelGenerator();

        ResultWorker worker = new ResultWorker(rabbitMQConnection, excelGenerator);
        worker.startListening(reportOutputPath, expectedCount);
    }

    public static void evaluateFromCmd(String[] args) throws Exception {
        CommandLineParser cliParser = new CommandLineParser();
        EvaluationContext context = cliParser.parse(args);

        log.info("Running direct evaluation for submission: {}", context.submissionPath());
        autojudge.CoreEvaluation.grading.GradingOrchestrator orchestrator = new autojudge.CoreEvaluation.grading.GradingOrchestrator();
        SubmissionResult result = orchestrator.evaluate(context);

        printSubmissionResult(result);
    }

    private static void printSubmissionResult(SubmissionResult result) {
        System.out.println("Evaluation Result:");
        System.out.println(result);
    }

    private static void printUsage() {
        System.err.println("AutoJudge Commands:");
        System.err.println("  Producer mode: java -jar autojudge.jar producer <assignment-path>");
        System.err.println("  Worker mode:   java -jar autojudge.jar worker");
        System.err.println("  Result Worker: java -jar autojudge.jar result-worker [reportOutputPath] [expectedCount]");
    }
}
