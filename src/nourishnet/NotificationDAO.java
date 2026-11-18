package nourishnet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class NotificationDAO {
    public void add(int userId, String message) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO Notifications(user_id,message) VALUES (?,?)")) {
            ps.setInt(1, userId);
            ps.setString(2, message);
            ps.executeUpdate();
        }
    }

    public void addToOrganization(int orgId, String message) throws SQLException {
        String sql = "INSERT INTO Notifications(user_id,message) "
                + "SELECT user_id,? FROM Organizations WHERE org_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, message);
            ps.setInt(2, orgId);
            ps.executeUpdate();
        }
    }

    public List<UserNotification> listForUser(int userId) throws SQLException {
        List<UserNotification> results = new ArrayList<>();
        String sql = "SELECT notification_id,message,created_at,is_read FROM Notifications "
                + "WHERE user_id=? ORDER BY created_at DESC,notification_id DESC LIMIT 50";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) results.add(new UserNotification(
                        rs.getInt("notification_id"), rs.getString("message"),
                        rs.getString("created_at"), rs.getBoolean("is_read")));
            }
        }
        return results;
    }

    public boolean markRead(int notificationId, int userId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE Notifications SET is_read=TRUE WHERE notification_id=? AND user_id=?")) {
            ps.setInt(1, notificationId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }
}
