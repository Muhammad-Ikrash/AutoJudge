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

    

    private final SubmissionResultRepository repository;
    private final autojudge.database.PlagiarismReportRepository plagiarismRepository;
    private final ExcelGenerator excelGenerator;
    private final EvaluationProducer producer;


    // private Path getAssignmentRootPath(){
    //     return Path.of(System.getProperty("user.dir"))
    //                                         .toAbsolutePath()
    //                                         .normalize()
    //                                         .getParent();
    // }

    public AssignmentController(SubmissionResultRepository repository,
                                autojudge.database.PlagiarismReportRepository plagiarismRepository,
                                ExcelGenerator excelGenerator,
                                EvaluationProducer producer) {
        this.repository = repository;
        this.plagiarismRepository = plagiarismRepository;
        this.excelGenerator = excelGenerator;
        this.producer = producer;
    }

    @GetMapping
    public ResponseEntity<List<autojudge.database.SubmissionResultRepository.AssignmentSummary>> getAssignments() {
        try {
            return ResponseEntity.ok(repository.findAllAssignmentSummaries());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/grade")
    public ResponseEntity<Map<String, Object>> gradeAssignment(
            @PathVariable("id") String assignmentId,
            @RequestParam("path") String assignmentPathStr,
            @RequestParam(value = "plagiarism", defaultValue = "false") boolean enablePlagiarism) {
        
        try {
            Path assignmentPath = Path.of(assignmentPathStr).toAbsolutePath().normalize();
            // Path expectedRoot = getAssignmentRootPath();
            
            // if (!assignmentPath.startsWith(expectedRoot)) {
            //     return ResponseEntity.badRequest().body(Map.of("error", "Invalid assignment path: must be within expected root directory."));
            // }
            if (!Files.exists(assignmentPath) || !Files.isDirectory(assignmentPath)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Assignment path does not exist or is not a directory: " + assignmentPathStr));
            }

            
            EvaluationProducer.ProduceResult result = producer.produceEvaluationJobs(assignmentId, assignmentPath, enablePlagiarism);
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "batchId", result.batchId(),
                    "jobsProduced", result.jobsProduced(),
                    "assignmentId", assignmentPath.getFileName() != null ? assignmentPath.getFileName().toString() : "assignment-1",
                    "message", "Grading jobs queued successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/students/{studentId}/rejudge")
    public ResponseEntity<Map<String, Object>> rejudgeStudent(
            @PathVariable("id") String assignmentId,
            @PathVariable("studentId") String studentId,
            @RequestParam("path") String assignmentPathStr) {
        try {
            Path assignmentPath = Path.of(assignmentPathStr).toAbsolutePath().normalize();
            if (!Files.exists(assignmentPath) || !Files.isDirectory(assignmentPath)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Assignment path does not exist or is not a directory: " + assignmentPathStr));
            }

            EvaluationProducer.ProduceResult result = producer.produceSingleStudentJob(assignmentId,assignmentPath, studentId);
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "batchId", result.batchId(),
                    "jobsProduced", result.jobsProduced(),
                    "assignmentId", assignmentId,
                    "studentId", studentId,
                    "message", "Rejudge queued successfully"
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

    @GetMapping("/{id}/plagiarism")
    public ResponseEntity<autojudge.PlagiarismDetection.model.PlagiarismReport> getPlagiarismReport(
            @PathVariable("id") String assignmentId,
            @RequestParam(value = "threshold", defaultValue = "0.0") double threshold) {
        try {
            return ResponseEntity.ok(plagiarismRepository.findByAssignmentIdAndThreshold(assignmentId, threshold));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/plagiarism/report")
    public ResponseEntity<Resource> getPlagiarismExcelReport(
            @PathVariable("id") String assignmentId,
            @RequestParam(value = "threshold", defaultValue = "0.0") double threshold) {
        try {
            autojudge.PlagiarismDetection.model.PlagiarismReport report = plagiarismRepository.findByAssignmentIdAndThreshold(assignmentId, threshold);
            Path tempFile = Files.createTempFile("plagiarism_" + assignmentId, ".xlsx");
            
            excelGenerator.generatePlagiarismReport(report, tempFile);
            
            byte[] data = Files.readAllBytes(tempFile);
            Files.deleteIfExists(tempFile);
            
            ByteArrayResource resource = new ByteArrayResource(data);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plagiarism_" + assignmentId + ".xlsx\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
                    
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAssignment(@PathVariable("id") String assignmentId) {
        try {
            plagiarismRepository.deleteById(assignmentId);
            repository.deleteById(assignmentId);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Assignment deleted successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
