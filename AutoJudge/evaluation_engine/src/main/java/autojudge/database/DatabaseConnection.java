package autojudge.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final String URL = "jdbc:h2:./data/autojudge;AUTO_SERVER=TRUE";
    
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL);
    }
}
