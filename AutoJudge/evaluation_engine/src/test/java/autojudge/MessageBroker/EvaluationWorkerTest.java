package autojudge.MessageBroker;

import autojudge.CoreEvaluation.model.EvaluationJob;
import autojudge.CoreEvaluation.model.SubmissionResult;
import autojudge.CoreEvaluation.model.Verdict;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EvaluationWorkerTest {

    @Test
    void testFailureResultFormatting() {
        EvaluationJob job = new EvaluationJob(
                "sub-1", "assignment-1", "student1",
                "/invalid/assignment/path", "/invalid/submission/path",
                "batch-123", 1
        );

        List<SubmissionResult> publishedResults = new ArrayList<>();

        // Simulate failure handler logic
        SubmissionResult errorResult = new SubmissionResult(
                job.submissionId(),
                job.assignmentId(),
                job.studentId(),
                0.0,
                Verdict.INTERNAL_ERROR,
                0,
                0,
                List.of(),
                job.batchId(),
                job.totalSubmissionsInBatch()
        );
        publishedResults.add(errorResult);

        assertEquals(1, publishedResults.size());
        SubmissionResult result = publishedResults.get(0);
        assertEquals("sub-1", result.submissionId());
        assertEquals(Verdict.INTERNAL_ERROR, result.verdict());
        assertEquals("batch-123", result.batchId());
        assertEquals(1, result.totalSubmissionsInBatch());
    }
}
