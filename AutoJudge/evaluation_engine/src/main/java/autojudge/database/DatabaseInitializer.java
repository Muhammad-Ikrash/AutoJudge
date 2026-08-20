package autojudge.database;

import java.sql.Connection;
import java.sql.Statement;

public class DatabaseInitializer {

    public void initialize() {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS submission_results (
                    student_id VARCHAR(255),
                    assignment_id VARCHAR(255),
                    submission_id VARCHAR(255),
                    score DOUBLE,
                    verdict VARCHAR(50),
                    passed_tests INT,
                    total_tests INT,
                    batch_id VARCHAR(255),
                    graded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (student_id, assignment_id)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS testcase_results (
                    student_id VARCHAR(255),
                    assignment_id VARCHAR(255),
                    test_case_name VARCHAR(255),
                    verdict VARCHAR(50),
                    graded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (student_id, assignment_id, test_case_name)
                )
            """);

            stmt.execute("""
                CREATE TABLE IF NOT EXISTS plagiarism_reports (
                    assignment_id VARCHAR(255),
                    submission_a VARCHAR(255),
                    submission_b VARCHAR(255),
                    similarity DOUBLE,
                    PRIMARY KEY (assignment_id, submission_a, submission_b)
                )
            """);

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }
}
