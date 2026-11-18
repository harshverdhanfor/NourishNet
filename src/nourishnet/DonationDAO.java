package nourishnet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class DonationDAO {

    public boolean addDonation(int donorId, String foodType, int qty,
                               String location, String pickupTime, String bestBy) throws SQLException {
        DonationValidator.validateDonation(foodType, qty, location, pickupTime, bestBy);
        String sql = "INSERT INTO Donations(donor_id,food_type,quantity,location,pickup_time,best_by,pickup_at,best_by_at) "
                + "VALUES (?,?,?,?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, donorId);
            ps.setString(2, foodType);
            ps.setInt(3, qty);
            ps.setString(4, location);
            ps.setString(5, pickupTime);
            ps.setString(6, bestBy);
            setDateTime(ps, 7, pickupTime);
            setDateTime(ps, 8, bestBy);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateDonation(int donationId, int donorId, String foodType, int qty,
                                  String location, String pickupTime, String bestBy) throws SQLException {
        DonationValidator.validateDonation(foodType, qty, location, pickupTime, bestBy);
        String sql = "UPDATE Donations SET food_type=?,quantity=?,location=?,pickup_time=?,best_by=?,"
                + "pickup_at=?,best_by_at=? WHERE donation_id=? AND donor_id=? AND status='AVAILABLE'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, foodType);
            ps.setInt(2, qty);
            ps.setString(3, location);
            ps.setString(4, pickupTime);
            ps.setString(5, bestBy);
            setDateTime(ps, 6, pickupTime);
            setDateTime(ps, 7, bestBy);
            ps.setInt(8, donationId);
            ps.setInt(9, donorId);
            return ps.executeUpdate() > 0;
        }
    }
    public Donation getDonationById(int donationId) throws SQLException {
        String sql = "SELECT donation_id, donor_id, food_type, quantity, location, pickup_time, best_by, "
                + "pickup_at,best_by_at,status FROM Donations WHERE donation_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, donationId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapDonation(rs) : null;
            }
        }
    }
    public List<Donation> getAvailableDonations() throws SQLException {
        return getDonations("AVAILABLE");
    }
    public List<Donation> getDonations(String status) throws SQLException {
        boolean allStatuses = status == null || status.equalsIgnoreCase("ALL");
        String sql = "SELECT donation_id, donor_id, food_type, quantity, location, pickup_time, best_by, "
                + "pickup_at,best_by_at,status "
                + "FROM Donations " + (allStatuses ? "" : "WHERE status=? ")
                + "ORDER BY donation_id DESC";
        List<Donation> donations = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (!allStatuses) ps.setString(1, status.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    donations.add(mapDonation(rs));
                }
            }
        }
        return donations;
    }

    public DashboardStats getDashboardStats() throws SQLException {
        String sql = "SELECT COUNT(*) AS total, "
                + "COALESCE(SUM(status='AVAILABLE'),0) AS available, "
                + "COALESCE(SUM(status='ACCEPTED'),0) AS accepted, "
                + "COALESCE(SUM(status='COLLECTED'),0) AS collected, "
                + "COALESCE(SUM(status='DELIVERED'),0) AS delivered, "
                + "COALESCE(SUM(CASE WHEN status='AVAILABLE' THEN quantity ELSE 0 END),0) AS meals "
                + "FROM Donations";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new DashboardStats(rs.getInt("total"), rs.getInt("available"),
                    rs.getInt("accepted"), rs.getInt("collected"),
                    rs.getInt("delivered"), rs.getInt("meals"));
        }
    }

    public OrganizationReport getOrganizationReport(int orgId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT d.donation_id) AS assigned, "
                + "COUNT(DISTINCT CASE WHEN d.status='DELIVERED' THEN d.donation_id END) AS delivered, "
                + "COALESCE(SUM(CASE WHEN d.status='DELIVERED' THEN d.quantity ELSE 0 END),0) AS meals "
                + "FROM Donations d JOIN PickupRequests pr ON pr.donation_id=d.donation_id "
                + "JOIN Volunteers v ON v.volunteer_id=pr.volunteer_id WHERE v.org_id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orgId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new OrganizationReport(rs.getInt("assigned"),
                        rs.getInt("delivered"), rs.getInt("meals"));
            }
        }
    }

    private static Donation mapDonation(ResultSet rs) throws SQLException {
        Timestamp pickupAt = rs.getTimestamp("pickup_at");
        Timestamp bestByAt = rs.getTimestamp("best_by_at");
        String pickup = pickupAt == null ? rs.getString("pickup_time")
                : pickupAt.toLocalDateTime().toString();
        String bestBy = bestByAt == null ? rs.getString("best_by")
                : bestByAt.toLocalDateTime().toString();
        return new Donation(rs.getInt("donation_id"), rs.getInt("donor_id"),
                rs.getString("food_type"), rs.getInt("quantity"),
                rs.getString("location"), pickup, bestBy, rs.getString("status"));
    }

    private static void setDateTime(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setTimestamp(index, null);
            return;
        }
        try {
            ps.setTimestamp(index, Timestamp.valueOf(LocalDateTime.parse(value.trim())));
        } catch (DateTimeParseException e) {
            ps.setTimestamp(index, null);
        }
    }

    public boolean acceptDonation(int donationId, int volunteerId) throws SQLException {
        String update = "UPDATE Donations SET status='ACCEPTED' "
                + "WHERE donation_id=? AND status='AVAILABLE'";
        String insert = "INSERT INTO PickupRequests(donation_id, volunteer_id) VALUES (?,?)";
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement updateStatement = con.prepareStatement(update);
                 PreparedStatement insertStatement = con.prepareStatement(insert)) {
                updateStatement.setInt(1, donationId);
                if (updateStatement.executeUpdate() == 0) {
                    con.rollback();
                    return false;
                }
                insertStatement.setInt(1, donationId);
                insertStatement.setInt(2, volunteerId);
                insertStatement.executeUpdate();
                con.commit();
                return true;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    public boolean markCollected(int donationId, int volunteerId) throws SQLException {
        String sql = "UPDATE Donations SET status='COLLECTED' "
                + "WHERE donation_id=? AND status='ACCEPTED' "
                + "AND EXISTS (SELECT 1 FROM PickupRequests "
                + "WHERE donation_id=? AND volunteer_id=?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, donationId);
            ps.setInt(2, donationId);
            ps.setInt(3, volunteerId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean markDelivered(int donationId, int volunteerId) throws SQLException {
        String requestSql = "SELECT pr.request_id, v.org_id "
                + "FROM PickupRequests pr JOIN Volunteers v ON v.volunteer_id=pr.volunteer_id "
                + "WHERE pr.donation_id=? AND pr.volunteer_id=? AND v.org_id IS NOT NULL "
                + "ORDER BY pr.request_id DESC LIMIT 1 FOR UPDATE";
        String updateSql = "UPDATE Donations SET status='DELIVERED' "
                + "WHERE donation_id=? AND status='COLLECTED'";
        String insertSql = "INSERT INTO Deliveries(request_id, org_id, delivered_at) "
                + "VALUES (?,?,CURRENT_TIMESTAMP)";
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement request = con.prepareStatement(requestSql);
                 PreparedStatement update = con.prepareStatement(updateSql);
                 PreparedStatement insert = con.prepareStatement(insertSql)) {
                request.setInt(1, donationId);
                request.setInt(2, volunteerId);
                int requestId;
                int orgId;
                try (ResultSet rs = request.executeQuery()) {
                    if (!rs.next()) {
                        con.rollback();
                        return false;
                    }
                    requestId = rs.getInt("request_id");
                    orgId = rs.getInt("org_id");
                }
                update.setInt(1, donationId);
                if (update.executeUpdate() == 0) {
                    con.rollback();
                    return false;
                }
                insert.setInt(1, requestId);
                insert.setInt(2, orgId);
                insert.executeUpdate();
                con.commit();
                return true;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }
}
