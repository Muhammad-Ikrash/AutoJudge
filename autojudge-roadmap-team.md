# AutoJudge — Project Roadmap

An automated grading system for DSA assignments, built around safe execution of student-submitted code. Think of it as a lightweight, custom version of DOMjudge: we scrape submissions, run them against test cases inside isolated containers, score them with partial marking, and hand a clean results sheet to the instructor to upload on Slate.

The core problem we're solving isn't "detecting malware" in the antivirus sense — it's running code we don't trust, safely. Docker and Kubernetes exist for exactly this kind of problem, so that's where most of the engineering effort goes.

---

## 1. Scraping submissions

We need student submissions pulled off the portal and organized before anything else can happen.

- Use Playwright to scrape assignment submissions from Slate into a folder structure: one folder per assignment, one subfolder per student.
- **Done when:** running the scraper against a real assignment gives us correctly organized folders with no manual cleanup needed.

## 2. Running code safely

This is the heart of the project. Each submission runs inside a disposable Docker container with no network access and strict CPU/memory/time limits. We copy in the test case, run it, capture the output and timing, then throw the container away.

- Docker, with minimal language-specific base images (gcc, openjdk, python-slim) to keep things fast and reduce what's exposed to student code.
- **Done when:** a correct submission runs fine, and a submission with an infinite loop gets killed cleanly at the timeout without affecting anything else.

## 3. Scoring

Raw pass/fail per test case isn't enough — we want partial marks, since that's how the instructor actually grades.

- Weight test cases differently (edge cases worth more), support flexible output comparison for problems with multiple valid answers.
- **Done when:** a submission passing 6/10 weighted test cases produces a sensible partial score, not just a binary result.

## 4. Handling many submissions at once

Around a deadline we'll have hundreds of submissions to grade, not one at a time.

- RabbitMQ as the job queue, with a pool of workers pulling jobs and running them through step 2.
- **Done when:** we can queue 100 submissions, they get processed in parallel, and a worker crashing mid-job doesn't lose that submission.

## 5. Moving to Kubernetes

Instead of long-running workers, each submission becomes its own short-lived Kubernetes Job — its own pod, its own resource limits, no outbound network access.

- Kubernetes Jobs API, with pods cleaned up automatically after they finish. If we want to go further on isolation, gVisor is worth evaluating as the container runtime.
- **Done when:** 50 submissions queued at once get scheduled and scaled automatically, and something trying to fork-bomb or eat memory only affects its own pod.

## 6. Backend + instructor-facing view

Everything above needs a way to actually be triggered and checked.

- Spring Boot API: trigger a scrape, trigger a grading run, pull results.
- **Done when:** the instructor can kick off a full grading run for an assignment and get results back without touching the internals.

## 7. Exporting results

- Export results in a format that matches Slate's gradebook import, so there's no manual reformatting before upload.
- **Done when:** a generated file imports cleanly into Slate.

## 8. Monitoring

- Prometheus + Grafana to track queue depth, grading time per submission, and failure rate — mainly useful for watching things hold up during a real deadline crunch.

## 9. (Optional, later) Plagiarism detection

Once the core system is solid, we can embed submitted code and flag suspiciously similar submissions for manual review. Not required for v1, but a natural extension once the pipeline exists.

---

**Suggested build order:** 1 → 2 → 3 gets us a working grader end to end. 4 and 5 are about scaling that to a real class size. 6 and 7 make it actually usable by the instructor. 8 is nice-to-have, 9 is a stretch goal.
