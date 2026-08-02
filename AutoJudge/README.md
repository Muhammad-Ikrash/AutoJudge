# AutoJudge

AutoJudge is a lightweight assignment grading workspace built around Docker-based execution.

The repository now keeps the core application layout at the top level:

- `assignments/` for assignment inputs, expected outputs, and submissions
- `docker/` for container images by language
- `src/main/java/org/autojudge/` for the application code
- `logs/` and `workspace/` for runtime artifacts
