# AutoJudge — Worker Lifecycle Management API

## Context

The Spring API can currently trigger `EvaluationProducer` (queue grading jobs) and read
results, but nothing in the API starts the consumers — `EvaluationWorker` and
`ResultWorker` still have to be launched manually from a terminal for queued jobs to
ever actually get processed. This spec adds API-driven control over worker lifecycle so
the whole pipeline (produce → consume → aggregate) can be started and scaled without a
terminal.

No changes are needed to `EvaluationWorker`'s or `ResultWorker`'s internal grading/
aggregation logic — this is orchestration around them, not a rewrite.

---

## New endpoints

| Endpoint | Purpose |
|---|---|
| `POST /api/workers?count=N` | Start `N` additional `EvaluationWorker` instances |
| `DELETE /api/workers?count=N` | Stop `N` running `EvaluationWorker` instances gracefully |
| `GET /api/workers/status` | Return current online `EvaluationWorker` count |
| `POST /api/result-worker/start` | Start the `ResultWorker` (idempotent — starting it while already running should not spawn a second instance) |
| `POST /api/system/start?workers=N` | Convenience endpoint: starts `ResultWorker` + `N` `EvaluationWorker` instances in one call |

---

## Design notes

### Worker registry — single source of truth

Add a Spring-managed `WorkerManager` bean holding a thread-safe collection of currently
running worker instances (e.g. `List<Thread>`, or an `ExecutorService` with tracked
`Future`s). All of `/api/workers`, `/api/workers` (DELETE), and `/api/workers/status`
operate against this one bean — don't scatter worker-tracking state across the
controller or let each endpoint maintain its own count independently.

### Graceful stop — no `Thread.stop()`

`EvaluationWorker` needs a way to be told "finish the current job, then stop consuming"
rather than being killed mid-execution, which could leave a Docker container orphaned
mid-grading (reopening exactly the container-state-leakage problem solved earlier for
the fork-bomb case).

Two acceptable approaches:
- A `volatile boolean running` flag checked at the top of the consume loop, so the
  current message finishes but no new one is pulled after the flag flips
- RabbitMQ consumer cancellation (`channel.basicCancel`) — lets in-flight work complete
  naturally while stopping new deliveries

Pick one and apply it consistently — don't mix both mechanisms across different stop
paths.

### `ResultWorker` idempotency

`ResultWorker` is singular by design — it owns one in-memory batch-completion tracking
map. `POST /api/result-worker/start` must check whether it's already running before
starting a new instance. Starting a second `ResultWorker` would create two independent
trackers racing on the same batch data, silently breaking the count-based completion
logic that was carefully built to avoid exactly this kind of race.

### `/api/system/start` is composition, not new logic

This endpoint should call the same internal start methods that `/api/workers` and
`/api/result-worker/start` already use — it should not contain a separate, parallel
startup implementation. Two code paths that both "start a worker" but are implemented
independently will drift out of sync over time (e.g. one gets the idempotency check
fixed later, the other doesn't).

---

## Acceptance criteria

- `GET /api/workers/status` accurately reflects the live count immediately after any
  add/remove call — no stale-read race
- Stopping a worker via `DELETE /api/workers` does not kill an in-flight grading job;
  the current exec completes before that worker instance actually shuts down
- Calling `POST /api/system/start` twice in a row does not double-start `ResultWorker`
  and does not leak orphaned worker threads from the first call
- `POST /api/result-worker/start` called while already running returns a clear
  "already running" response rather than silently starting a duplicate

---

## Explicitly out of scope for this ticket

- Persisting desired worker count across an API process restart — if the Spring app
  itself restarts, workers must be re-started via a fresh API call; no auto-recovery
  logic required for this scope
- Auto-scaling based on queue depth — this ticket is manual count control only
