package autojudge;

import autojudge.grading.GradingOrchestrator;
import autojudge.loader.CommandLineParser;
import autojudge.model.EvaluationContext;
import autojudge.model.SubmissionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * CLI entry point for AutoJudge Evaluation Engine.
 */
public final class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private Main() {
    }

    public static void main(String[] args) {
        try {
            CommandLineParser cliParser = new CommandLineParser();
            EvaluationContext context = cliParser.parse(args);

            GradingOrchestrator orchestrator = new GradingOrchestrator();
            SubmissionResult results = orchestrator.evaluate(context);

            printSubmissionResult(results);
        } catch (Exception e) {
            log.error("Evaluation failed: {}", e.getMessage());
            printUsage();
            System.exit(1);
        }
    }
    
    private static void printSubmissionResult(SubmissionResult result) {
        System.out.println(result);
    }

    private static void printUsage() {
        System.err.println("Usage: java -jar evaluation_engine.jar <submissionsRoot> <inputDirectory> <outputDirectory> <configFile> <weightsFile>");
    }
}
