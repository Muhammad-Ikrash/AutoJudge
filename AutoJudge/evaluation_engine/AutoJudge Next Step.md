# AutoJudge — H2 Database Integration Architecture

## Context

Next milestone after RabbitMQ + JPlag: persist grading and plagiarism results properly
instead of only generating Excel. Excel becomes an export format generated from the
database, not the data's home.

## Access boundary (who reads/writes what)

| Component | DB access |
|---|---|
| **Producer** | Owns schema (creates tables on first run). Writes `PlagiarismReport` data. |
| **ResultWorker** | Writes `SubmissionResult` data (per-testcase results for each student). |
| **EvaluationWorker (normal worker)** | **No DB access at all.** Purely consumes evaluation jobs, runs grading, publishes results to the result queue — stays exactly as decoupled as it is today. |

Rationale: single-writer-per-table avoids the concurrent-write problem already
deliberately avoided once before (the async-Excel-mutation idea that was scoped out).
Different tables for producer-owned data vs. ResultWorker-owned data — no two processes
should ever write to the same table.

## H2 configuration

- **Embedded file mode**, not a separately managed server — matches the "no setup
  required" constraint from the business-pivot distribution model (TA runs the `.jar`
  on their own machine).
- **`AUTO_SERVER=TRUE`** — allows multiple JVM processes (producer + ResultWorker) to
  hold concurrent connections to the same embedded database file without needing a
  manually-run separate H2 server process. This is what makes the multi-process write
  boundary above actually work correctly.

## Schema ownership

- **Producer creates the schema** on startup (tables, if-not-exists DDL or a lightweight
  migration step) — single source of truth for schema creation, avoids a race where two
  process types try to create the same table on first run.

## Rejudge design (future — not built yet, but schema needs to support it now)

- Rejudge granularity needed later: single question for all students, or a single
  student across an assignment.
- **Requires a stable, natural composite key** on `SubmissionResult` —
  `(studentId, assignmentId, testCaseId)` or equivalent — not just an auto-increment ID.
  This is what makes `WHERE studentId = ? AND assignmentId = ?` a clean, addressable
  operation instead of an ambiguous row search. Get this right in the initial schema;
  retrofitting a natural key after real grading data exists means a migration against
  production data.

## Overwrite-in-place decision

- **Rejudge overwrites in place** — no historical/versioned rows kept. Chosen
  deliberately: old results add no value and would complicate Excel generation from
  the DB.
- **Known tradeoff, accepted knowingly:** no audit trail. If a TA rejudges and a
  student's score changes, there's no record of when or what it changed from. Low
  likelihood of mattering, but worth being aware of given this is now a
  multi-instructor distributed product, not a personal script — a grade dispute with
  zero audit trail is the realistic exposure.
- **Mitigation kept, at near-zero cost:** add a `gradedAt` (timestamp) column even in
  the overwrite-in-place design. Doesn't give full history, but at least answers "when
  was this last graded" without taking on versioning complexity.

## Open items / things to decide when actually implementing

- Producer's DB access for plagiarism — confirmed as writing `PlagiarismReport`; if it
  also needs to *read* existing `SubmissionResult`s first (e.g. to know which
  submissions exist before running JPlag), that read path needs to be designed too.
- Rejudge functionality itself — deferred until frontend is built. Schema should support
  it (see composite key above) even though the feature isn't implemented yet.
