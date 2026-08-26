package autojudge.api;

import autojudge.CoreEvaluation.grading.EvaluationEngine;
import autojudge.MessageBroker.EvaluationWorker;
import autojudge.MessageBroker.RabbitMQConnection;
import autojudge.MessageBroker.ResultPublisher;
import autojudge.MessageBroker.ResultWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Spring-managed singleton that is the single source of truth for all running worker instances.
 * All lifecycle endpoints operate against this one bean — worker-tracking state is never
 * scattered across controllers.
 */
@Component
public class WorkerManager {

    private static final Logger log = LoggerFactory.getLogger(WorkerManager.class);

    private final RabbitMQConnection rabbitMQConnection;

    /** Tracks every live EvaluationWorker thread. */
    private final List<EvaluationWorker> runningEvaluationWorkers = new CopyOnWriteArrayList<>();

    /** The single ResultWorker instance — null means not started. */
    private volatile ResultWorker resultWorker;

    public WorkerManager(RabbitMQConnection rabbitMQConnection) {
        this.rabbitMQConnection = rabbitMQConnection;
    }

    // -----------------------------------------------------------------------
    // EvaluationWorker lifecycle
    // -----------------------------------------------------------------------

    /**
     * Starts {@code count} additional EvaluationWorker instances, each on its own daemon thread.
     */
    public int startEvaluationWorkers(int count) {
        int started = 0;
        for (int i = 0; i < count; i++) {
            try {
                EvaluationWorker worker = new EvaluationWorker(
                        rabbitMQConnection,
                        new EvaluationEngine(),
                        new ResultPublisher(rabbitMQConnection)
                );
                Thread thread = new Thread(() -> {
                    try {
                        worker.startListening();
                        // Block until the channel is closed (graceful stop)
                        while (worker.isRunning()) {
                            Thread.sleep(500);
                        }
                    } catch (IOException e) {
                        log.error("EvaluationWorker thread failed to start", e);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "evaluation-worker-" + System.nanoTime());
                thread.setDaemon(true);
                thread.start();
                runningEvaluationWorkers.add(worker);
                started++;
                log.info("Started EvaluationWorker #{} (total={})", i + 1, runningEvaluationWorkers.size());
            } catch (Exception e) {
                log.error("Failed to start EvaluationWorker instance #{}", i + 1, e);
            }
        }
        return started;
    }

    /**
     * Gracefully stops {@code count} running EvaluationWorker instances (LIFO order).
     * In-flight jobs complete before the channel is closed.
     */
    public int stopEvaluationWorkers(int count) {
        int stopped = 0;
        for (int i = 0; i < count && !runningEvaluationWorkers.isEmpty(); i++) {
            EvaluationWorker worker = runningEvaluationWorkers.remove(runningEvaluationWorkers.size() - 1);
            worker.stop();
            stopped++;
            log.info("Stopped EvaluationWorker (remaining={})", runningEvaluationWorkers.size());
        }
        return stopped;
    }

    /** Returns the current count of live EvaluationWorker instances. */
    public int evaluationWorkerCount() {
        // Prune any workers whose channels have been closed externally
        runningEvaluationWorkers.removeIf(w -> !w.isRunning());
        return runningEvaluationWorkers.size();
    }

    // -----------------------------------------------------------------------
    // ResultWorker lifecycle (idempotent singleton)
    // -----------------------------------------------------------------------

    /**
     * Starts the ResultWorker if not already running.
     * Returns {@code true} if a new instance was started, {@code false} if already running.
     */
    public synchronized boolean startResultWorker() {
        if (resultWorker != null && resultWorker.isRunning()) {
            log.info("ResultWorker is already running — skipping duplicate start.");
            return false;
        }
        try {
            resultWorker = new ResultWorker(rabbitMQConnection);
            Thread thread = new Thread(() -> {
                try {
                    resultWorker.startListening();
                    while (resultWorker.isRunning()) {
                        Thread.sleep(500);
                    }
                } catch (IOException e) {
                    log.error("ResultWorker thread failed to start", e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "result-worker");
            thread.setDaemon(true);
            thread.start();
            log.info("ResultWorker started.");
            return true;
        } catch (Exception e) {
            log.error("Failed to start ResultWorker", e);
            return false;
        }
    }

    public boolean isResultWorkerRunning() {
        return resultWorker != null && resultWorker.isRunning();
    }

    // -----------------------------------------------------------------------
    // Combined start
    // -----------------------------------------------------------------------

    /**
     * Convenience: starts ResultWorker (idempotent) + {@code workerCount} EvaluationWorkers.
     */
    public Map<String, Object> startSystem(int workerCount) {
        boolean resultWorkerStarted = startResultWorker();
        int evaluationWorkersStarted = startEvaluationWorkers(workerCount);
        return Map.of(
                "resultWorkerStarted", resultWorkerStarted,
                "evaluationWorkersStarted", evaluationWorkersStarted,
                "totalEvaluationWorkers", evaluationWorkerCount()
        );
    }
}
