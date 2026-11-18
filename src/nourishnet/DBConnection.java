package nourishnet;
import java.sql.*;
public class DBConnection{
    private static final String URL = System.getenv().getOrDefault(
            "NOURISHNET_DB_URL", "jdbc:mysql://localhost:3306/nourishnet");
    private static final String USER = System.getenv().getOrDefault("NOURISHNET_DB_USER", "root");
    private static final String PASS = System.getenv().getOrDefault("NOURISHNET_DB_PASS", "your_password");

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
