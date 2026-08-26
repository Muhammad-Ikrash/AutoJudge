package autojudge.database;

import autojudge.CoreEvaluation.model.SubmissionResult;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class SubmissionResultRepository {
    public void save(SubmissionResult result) {
        String sql = """
            MERGE INTO submission_results (student_id, assignment_id, submission_id, score, verdict, passed_tests, total_tests, batch_id, graded_at)
            KEY(student_id, assignment_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
        """;
        
        String deleteTestCasesSql = "DELETE FROM testcase_results WHERE student_id = ? AND assignment_id = ?";
        String insertTestCaseSql = """
            INSERT INTO testcase_results (student_id, assignment_id, test_case_name, verdict, graded_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
        """;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, result.studentId());
                pstmt.setString(2, result.assignmentId());
                pstmt.setString(3, result.submissionId());
                pstmt.setDouble(4, result.score());
                pstmt.setString(5, result.verdict().name());
                pstmt.setInt(6, result.passedTests());
                pstmt.setInt(7, result.totalTests());
                pstmt.setString(8, result.batchId());
                pstmt.executeUpdate();
            }
            
            try (PreparedStatement delStmt = conn.prepareStatement(deleteTestCasesSql)) {
                delStmt.setString(1, result.studentId());
                delStmt.setString(2, result.assignmentId());
                delStmt.executeUpdate();
            }
            
            if (result.testCasesResults() != null && !result.testCasesResults().isEmpty()) {
                try (PreparedStatement insStmt = conn.prepareStatement(insertTestCaseSql)) {
                    for (autojudge.CoreEvaluation.model.testCaseResult tc : result.testCasesResults()) {
                        insStmt.setString(1, result.studentId());
                        insStmt.setString(2, result.assignmentId());
                        insStmt.setString(3, tc.testCaseId());
                        insStmt.setString(4, tc.verdict().name());
                        insStmt.addBatch();
                    }
                    insStmt.executeBatch();
                }
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save submission result", e);
        }
    }

    public java.util.List<SubmissionResult> findAllByAssignmentId(String assignmentId) {
        String queryResults = "SELECT * FROM submission_results WHERE assignment_id = ?";
        String queryTests = "SELECT * FROM testcase_results WHERE assignment_id = ?";
        
        java.util.List<SubmissionResult> results = new java.util.ArrayList<>();
        java.util.Map<String, java.util.List<autojudge.CoreEvaluation.model.testCaseResult>> testsMap = new java.util.HashMap<>();
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement testStmt = conn.prepareStatement(queryTests)) {
                testStmt.setString(1, assignmentId);
                try (java.sql.ResultSet testRs = testStmt.executeQuery()) {
                    while (testRs.next()) {
                        String studentId = testRs.getString("student_id");
                        String testCaseName = testRs.getString("test_case_name");
                        autojudge.CoreEvaluation.model.Verdict testVerdict = autojudge.CoreEvaluation.model.Verdict.valueOf(testRs.getString("verdict"));
                        testsMap.computeIfAbsent(studentId, k -> new java.util.ArrayList<>())
                                .add(new autojudge.CoreEvaluation.model.testCaseResult(testCaseName, testVerdict));
                    }
                }
            }
            
            try (PreparedStatement pstmt = conn.prepareStatement(queryResults)) {
                pstmt.setString(1, assignmentId);
                try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String studentId = rs.getString("student_id");
                        String submissionId = rs.getString("submission_id");
                        double score = rs.getDouble("score");
                        autojudge.CoreEvaluation.model.Verdict verdict = autojudge.CoreEvaluation.model.Verdict.valueOf(rs.getString("verdict"));
                        int passedTests = rs.getInt("passed_tests");
                        int totalTests = rs.getInt("total_tests");
                        String batchId = rs.getString("batch_id");
                        
                        java.util.List<autojudge.CoreEvaluation.model.testCaseResult> tests = testsMap.getOrDefault(studentId, new java.util.ArrayList<>());
                        results.add(new SubmissionResult(submissionId, assignmentId, studentId, score, verdict, passedTests, totalTests, tests, batchId, 0));
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to read submission results", e);
        }
        return results;
    }
}
