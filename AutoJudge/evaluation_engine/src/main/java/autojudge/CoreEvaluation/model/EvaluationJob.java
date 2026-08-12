package autojudge.CoreEvaluation.model;

import java.io.Serializable;

/**
 * Serializable logical request representing one submission evaluation job.
 * Carried across process boundaries via RabbitMQ.
 */
public record EvaluationJob(
        String submissionId,
        String assignmentId,
        String studentId,
        String assignmentPath,
        String submissionPath
) implements Serializable {
}
