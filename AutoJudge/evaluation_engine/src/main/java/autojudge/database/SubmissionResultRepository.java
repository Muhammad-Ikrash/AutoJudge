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
}
