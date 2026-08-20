package autojudge.api;

import autojudge.MessageBroker.EvaluationProducer;
import autojudge.MessageBroker.RabbitMQConnection;
import autojudge.database.SubmissionResultRepository;
import autojudge.CoreEvaluation.model.SubmissionResult;
import autojudge.reporting.ExcelGenerator;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final SubmissionResultRepository repository = new SubmissionResultRepository();
    private final ExcelGenerator excelGenerator = new ExcelGenerator();

    @PostMapping("/{id}/grade")
    public ResponseEntity<Map<String, Object>> gradeAssignment(
            @PathVariable("id") String assignmentId,
            @RequestParam("path") String assignmentPathStr,
            @RequestParam(value = "plagiarism", defaultValue = "false") boolean enablePlagiarism) {
        
        try {
            Path assignmentPath = Path.of(assignmentPathStr);
            RabbitMQConnection connection = new RabbitMQConnection();
            EvaluationProducer producer = new EvaluationProducer(connection);
            
            int count = producer.produceEvaluationJobs(assignmentPath, enablePlagiarism);
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "jobsProduced", count,
                    "assignmentId", assignmentPath.getFileName() != null ? assignmentPath.getFileName().toString() : "assignment-1",
                    "message", "Grading jobs queued successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/results")
    public ResponseEntity<List<SubmissionResult>> getResults(@PathVariable("id") String assignmentId) {
        try {
            List<SubmissionResult> results = repository.findAllByAssignmentId(assignmentId);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<Resource> getReport(@PathVariable("id") String assignmentId) {
        try {
            List<SubmissionResult> results = repository.findAllByAssignmentId(assignmentId);
            Path tempFile = Files.createTempFile("report_" + assignmentId, ".xlsx");
            
            excelGenerator.generateReport(new java.util.ArrayList<>(results), tempFile);
            
            byte[] data = Files.readAllBytes(tempFile);
            Files.deleteIfExists(tempFile);
            
            ByteArrayResource resource = new ByteArrayResource(data);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report_" + assignmentId + ".xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
                    
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
