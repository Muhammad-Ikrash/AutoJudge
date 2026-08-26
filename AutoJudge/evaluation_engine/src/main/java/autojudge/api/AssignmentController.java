package autojudge.api;

import autojudge.CoreEvaluation.loader.AssignmentLoader;
import autojudge.CoreEvaluation.model.Assignment;
import autojudge.MessageBroker.EvaluationProducer;
import autojudge.database.SubmissionResultRepository;
import autojudge.CoreEvaluation.model.SubmissionResult;
import autojudge.reporting.ExcelGenerator;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

    private final SubmissionResultRepository repository;
    private final autojudge.database.PlagiarismReportRepository plagiarismRepository;
    private final ExcelGenerator excelGenerator;
    private final EvaluationProducer producer;
    private final WorkerManager workerManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public AssignmentController(SubmissionResultRepository repository,
                                autojudge.database.PlagiarismReportRepository plagiarismRepository,
                                ExcelGenerator excelGenerator,
                                EvaluationProducer producer,
                                WorkerManager workerManager) {
        this.repository = repository;
        this.plagiarismRepository = plagiarismRepository;
        this.excelGenerator = excelGenerator;
        this.producer = producer;
        this.workerManager = workerManager;
    }

    // private Path getAssignmentsRoot() {
    //     return Path.of(System.getProperty("user.dir"), "assignments").toAbsolutePath();
    // }
    private Path getAssignmentsRoot() {
        return Path.of(System.getProperty("user.dir"))
                   .getParent()
                   .resolve("assignments")
                   .toAbsolutePath();
    }
    
    private Path getAssignmentPath(String id) {
        return getAssignmentsRoot().resolve(id);
    }

    // 1. GET /api/assignments
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listAssignments() {
        Path root = getAssignmentsRoot();
        List<Map<String, Object>> list = new ArrayList<>();
        if (Files.exists(root) && Files.isDirectory(root)) {
            try (var stream = Files.list(root)) {
                stream.filter(Files::isDirectory).forEach(p -> {
                    String id = p.getFileName().toString();
                    list.add(getAssignmentSummary(id, p));
                });
            } catch (Exception e) {}
        }
        return ResponseEntity.ok(list);
    }

    // 2. GET /api/assignments/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAssignment(@PathVariable("id") String id) {
        Path p = getAssignmentPath(id);
        if (!Files.exists(p)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(getAssignmentSummary(id, p));
    }

    private Map<String, Object> getAssignmentSummary(String id, Path p) {
        int count = 0;
        Path subs = p.resolve("submissions");
        if (Files.exists(subs) && Files.isDirectory(subs)) {
            try (var s = Files.list(subs)) {
                count = (int) s.filter(Files::isDirectory).count();
            } catch (Exception e) {}
        }
        Assignment config = null;
        try {
            config = AssignmentLoader.load(p);
        } catch (Exception e) {}
        
        String status = "idle";
        if (workerManager.getResultWorker() != null) {
            String batchId = workerManager.getResultWorker().getLatestBatchIdForAssignment(id);
            if (batchId != null) {
                Map<String, Object> batchStatus = workerManager.getResultWorker().getBatchStatus(batchId);
                int completed = (int) batchStatus.getOrDefault("completed", 0);
                int total = (int) batchStatus.getOrDefault("total", 0);
                if (total > 0) {
                    status = completed < total ? "grading" : "completed";
                }
            }
        }
        
        if ("idle".equals(status)) {
            if (repository.countByAssignmentId(id) > 0) {
                status = "completed";
            }
        }
        
        return Map.of(
            "id", id,
            "submissionCount", count,
            "status", status,
            "config", config != null ? config : Map.of()
        );
    }

    // 3. GET /api/assignments/{id}/status
    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getAssignmentStatus(@PathVariable("id") String id) {
        if (workerManager.getResultWorker() != null) {
            String batchId = workerManager.getResultWorker().getLatestBatchIdForAssignment(id);
            if (batchId != null) {
                return ResponseEntity.ok(workerManager.getResultWorker().getBatchStatus(batchId));
            }
        }
        
        int dbCount = repository.countByAssignmentId(id);
        if (dbCount > 0) {
            return ResponseEntity.ok(Map.of("completed", dbCount, "total", dbCount));
        }
        
        return ResponseEntity.ok(Map.of("completed", 0, "total", 0));
    }

    // 4 & 5. POST /api/assignments/{id}/rejudge
    @PostMapping("/{id}/rejudge")
    public ResponseEntity<Map<String, Object>> rejudgeAssignment(
            @PathVariable("id") String assignmentId,
            @RequestParam(value = "studentId", required = false) String studentId,
            @RequestParam(value = "testCaseId", required = false) String testCaseId) {
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Rejudge triggered",
                "filters", Map.of("studentId", studentId != null ? studentId : "", "testCaseId", testCaseId != null ? testCaseId : "")
        ));
    }

    // 6. GET /api/assignments/{id}/testcases
    @GetMapping("/{id}/testcases")
    public ResponseEntity<List<Map<String, Object>>> getTestCases(@PathVariable("id") String id) {
        Path p = getAssignmentPath(id);
        if (!Files.exists(p)) return ResponseEntity.notFound().build();
        
        Path inputDir = p.resolve("input");
        Path expectedDir = p.resolve("expected");
        Path weightsFile = p.resolve("weights.json");
        
        Map<String, Integer> weights = readWeights(weightsFile);
        List<Map<String, Object>> testcases = new ArrayList<>();
        
        if (Files.exists(inputDir) && Files.isDirectory(inputDir)) {
            try (var stream = Files.list(inputDir)) {
                stream.forEach(file -> {
                    String filename = file.getFileName().toString();
                    String name = filename.replace(".txt", "");
                    boolean hasExpected = Files.exists(expectedDir.resolve(filename));
                    int weight = weights.getOrDefault(name, 1);
                    testcases.add(Map.of(
                        "id", name,
                        "inputFile", filename,
                        "outputFile", hasExpected ? filename : "",
                        "weight", weight
                    ));
                });
            } catch (Exception e) {}
        }
        return ResponseEntity.ok(testcases);
    }
    
    private Map<String, Integer> readWeights(Path weightsFile) {
        if (!Files.exists(weightsFile)) return new java.util.HashMap<>();
        try {
            return mapper.readValue(weightsFile.toFile(), new TypeReference<Map<String, Integer>>() {});
        } catch (Exception e) {
            return new java.util.HashMap<>();
        }
    }
    
    private void writeWeights(Path weightsFile, Map<String, Integer> weights) {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(weightsFile.toFile(), weights);
        } catch (Exception e) {}
    }

    // 7. POST /api/assignments/{id}/testcases
    @PostMapping(value = "/{id}/testcases", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> addTestCase(
            @PathVariable("id") String id,
            @RequestParam("name") String name,
            @RequestParam("input") MultipartFile input,
            @RequestParam("expected") MultipartFile expected,
            @RequestParam(value = "weight", defaultValue = "1") int weight) {
        
        Path p = getAssignmentPath(id);
        try {
            Files.createDirectories(p.resolve("input"));
            Files.createDirectories(p.resolve("expected"));
            
            String filename = name + ".txt";
            input.transferTo(p.resolve("input").resolve(filename).toFile());
            expected.transferTo(p.resolve("expected").resolve(filename).toFile());
            
            Path weightsFile = p.resolve("weights.json");
            Map<String, Integer> weights = readWeights(weightsFile);
            weights.put(name, weight);
            writeWeights(weightsFile, weights);
            
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 8. DELETE /api/assignments/{id}/testcases/{testCaseId}
    @DeleteMapping("/{id}/testcases/{testCaseId}")
    public ResponseEntity<Map<String, Object>> deleteTestCase(
            @PathVariable("id") String id,
            @PathVariable("testCaseId") String testCaseId) {
        Path p = getAssignmentPath(id);
        String filename = testCaseId + ".txt";
        try {
            Files.deleteIfExists(p.resolve("input").resolve(filename));
            Files.deleteIfExists(p.resolve("expected").resolve(filename));
            
            Path weightsFile = p.resolve("weights.json");
            Map<String, Integer> weights = readWeights(weightsFile);
            if (weights.remove(testCaseId) != null) {
                writeWeights(weightsFile, weights);
            }
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 9. PUT /api/assignments/{id}/testcases/{testCaseId}
    @PutMapping("/{id}/testcases/{testCaseId}")
    public ResponseEntity<Map<String, Object>> updateTestCase(
            @PathVariable("id") String id,
            @PathVariable("testCaseId") String testCaseId,
            @RequestBody Map<String, Object> body) {
        Path p = getAssignmentPath(id);
        if (body.containsKey("weight")) {
            // Can be Integer or String depending on parser, handle both safely
            int weight;
            Object wObj = body.get("weight");
            if (wObj instanceof Number) {
                weight = ((Number) wObj).intValue();
            } else {
                weight = Integer.parseInt(wObj.toString());
            }
            
            Path weightsFile = p.resolve("weights.json");
            Map<String, Integer> weights = readWeights(weightsFile);
            weights.put(testCaseId, weight);
            writeWeights(weightsFile, weights);
        }
        return ResponseEntity.ok(Map.of("status", "success"));
    }

    // 10. GET /api/assignments/{id}/config
    @GetMapping("/{id}/config")
    public ResponseEntity<Assignment> getConfig(@PathVariable("id") String id) {
        Path p = getAssignmentPath(id);
        try {
            Assignment config = AssignmentLoader.load(p);
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    // 11. PUT /api/assignments/{id}/config
    @PutMapping("/{id}/config")
    public ResponseEntity<Map<String, Object>> updateConfig(
            @PathVariable("id") String id,
            @RequestBody Assignment config) {
        Path p = getAssignmentPath(id);
        try {
            AssignmentLoader.saveConfig(AssignmentLoader.resolveConfigPath(p), config);
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
    
    // Original methods...
    @PostMapping("/{id}/grade")
    public ResponseEntity<Map<String, Object>> gradeAssignment(
            @PathVariable("id") String assignmentId,
            @RequestParam(value = "path", required = false) String assignmentPathStr,
            @RequestParam(value = "plagiarism", defaultValue = "false") boolean enablePlagiarism) {
        
        try {
            Path assignmentPath = assignmentPathStr != null ? Path.of(assignmentPathStr).toAbsolutePath().normalize() : getAssignmentPath(assignmentId);
            Path expectedRoot = getAssignmentsRoot().normalize();
            
            if (!assignmentPath.startsWith(expectedRoot)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid assignment path: must be within expected root directory."));
            }
            if (!Files.exists(assignmentPath) || !Files.isDirectory(assignmentPath)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Assignment path does not exist or is not a directory: " + assignmentPath.toString()));
            }

            int count = producer.produceEvaluationJobs(assignmentPath, enablePlagiarism);
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "jobsProduced", count,
                    "assignmentId", assignmentId,
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
    
    // 12. POST /api/assignments/{id}/plagiarism
    @PostMapping("/{id}/plagiarism")
    public ResponseEntity<Map<String, Object>> runPlagiarismCheck(@PathVariable("id") String id) {
        try {
            Path p = getAssignmentPath(id);
            autojudge.pipeline.PipelineOrchestrator pipeline = new autojudge.pipeline.PipelineOrchestrator();
            java.util.Optional<autojudge.PlagiarismDetection.model.PlagiarismReport> reportOpt = 
                pipeline.processPlagiarismStage(id, p, "cpp", true);
            
            reportOpt.ifPresent(report -> {
                plagiarismRepository.save(report);
            });
            return ResponseEntity.ok(Map.of("status", "success", "message", "Plagiarism check completed"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // 13. GET /api/assignments/{id}/plagiarism-results
    @GetMapping("/{id}/plagiarism-results")
    public ResponseEntity<autojudge.PlagiarismDetection.model.PlagiarismReport> getPlagiarismResults(
            @PathVariable("id") String assignmentId,
            @RequestParam(value = "threshold", defaultValue = "0.0") double threshold) {
        try {
            return ResponseEntity.ok(plagiarismRepository.findByAssignmentIdAndThreshold(assignmentId, threshold));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // 14. GET /api/assignments/{id}/plagiarism-report
    @GetMapping({"/{id}/plagiarism-report", "/{id}/plagiarism/report"})
    public ResponseEntity<Resource> getPlagiarismReport(
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
}
