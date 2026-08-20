package autojudge.database;

import autojudge.PlagiarismDetection.model.PlagiarismReport;
import autojudge.PlagiarismDetection.model.SimilarityPair;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class PlagiarismReportRepository {
    public void save(PlagiarismReport report) {
        String deleteSql = "DELETE FROM plagiarism_reports WHERE assignment_id = ?";
        String insertSql = """
            INSERT INTO plagiarism_reports (assignment_id, submission_a, submission_b, similarity)
            VALUES (?, ?, ?, ?)
        """;
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setString(1, report.assignmentId());
                deleteStmt.executeUpdate();
            }
            
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                for (SimilarityPair pair : report.similarities()) {
                    insertStmt.setString(1, report.assignmentId());
                    insertStmt.setString(2, pair.submissionA());
                    insertStmt.setString(3, pair.submissionB());
                    insertStmt.setDouble(4, pair.similarity());
                    insertStmt.addBatch();
                }
                insertStmt.executeBatch();
            }
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save plagiarism report", e);
        }
    }
}
