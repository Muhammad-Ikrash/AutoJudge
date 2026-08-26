package autojudge.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for worker lifecycle management.
 * Delegates all state changes to WorkerManager — this controller contains no worker-tracking logic.
 */
@RestController
public class WorkerController {

    private final WorkerManager workerManager;

    public WorkerController(WorkerManager workerManager) {
        this.workerManager = workerManager;
    }

    /** Start N additional EvaluationWorker instances. */
    @PostMapping("/api/workers")
    public ResponseEntity<Map<String, Object>> startWorkers(
            @RequestParam(value = "count", defaultValue = "1") int count) {
        int started = workerManager.startEvaluationWorkers(count);
        return ResponseEntity.ok(Map.of(
                "started", started,
                "totalEvaluationWorkers", workerManager.evaluationWorkerCount()
        ));
    }

    /** Gracefully stop N running EvaluationWorker instances. */
    @DeleteMapping("/api/workers")
    public ResponseEntity<Map<String, Object>> stopWorkers(
            @RequestParam(value = "count", defaultValue = "1") int count) {
        int stopped = workerManager.stopEvaluationWorkers(count);
        return ResponseEntity.ok(Map.of(
                "stopped", stopped,
                "totalEvaluationWorkers", workerManager.evaluationWorkerCount()
        ));
    }

    /** Return the current live EvaluationWorker count. */
    @GetMapping("/api/workers/status")
    public ResponseEntity<Map<String, Object>> workerStatus() {
        return ResponseEntity.ok(Map.of(
                "totalEvaluationWorkers", workerManager.evaluationWorkerCount(),
                "resultWorkerRunning", workerManager.isResultWorkerRunning(),
                "queuedJobs", workerManager.getQueuedJobs(),
                "lastBatchId", workerManager.getResultWorker() != null ? workerManager.getResultWorker().getLatestBatchId() : "—"
        ));
    }

    /** Start the ResultWorker (idempotent — no duplicate spawning). */
    @PostMapping("/api/result-worker/start")
    public ResponseEntity<Map<String, Object>> startResultWorker() {
        boolean started = workerManager.startResultWorker();
        if (started) {
            return ResponseEntity.ok(Map.of("status", "started"));
        }
        return ResponseEntity.ok(Map.of("status", "already_running",
                "message", "ResultWorker is already running. No duplicate instance was created."));
    }

    /** Convenience: start ResultWorker + N EvaluationWorkers in one call. */
    @PostMapping("/api/system/start")
    public ResponseEntity<Map<String, Object>> startSystem(
            @RequestParam(value = "workers", defaultValue = "1") int workerCount) {
        try {
            return ResponseEntity.ok(workerManager.startSystem(workerCount));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage() != null ? e.getMessage() : e.getClass().getName()));
        }
    }

    /** Return the status of a specific batch. */
    @GetMapping("/api/batches/{batchId}/status")
    public ResponseEntity<Map<String, Object>> getBatchStatus(@PathVariable("batchId") String batchId) {
        if (workerManager.getResultWorker() != null) {
            return ResponseEntity.ok(workerManager.getResultWorker().getBatchStatus(batchId));
        }
        return ResponseEntity.ok(Map.of("completed", 0, "total", 0));
    }
}
