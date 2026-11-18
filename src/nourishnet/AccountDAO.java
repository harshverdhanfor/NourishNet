package nourishnet;

import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AccountDAO {

    public UserAccount register(String name, String email, String phone, String password,
                                String role, String organizationName, String address,
                                int organizationId) throws SQLException, GeneralSecurityException {
        name = cleanRequired(name, "Enter your name.");
        email = cleanRequired(email, "Enter your email.");
        phone = phone == null ? "" : phone.trim();
        role = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 100) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }
        if (name.length() > 100 || phone.length() > 15) {
            throw new IllegalArgumentException("Name or phone number is too long.");
        }
        if (!role.equals("DONOR") && !role.equals("VOLUNTEER") && !role.equals("NGO")) {
            throw new IllegalArgumentException("Choose Donor, Volunteer, or NGO.");
        }
        if (role.equals("NGO")) {
            organizationName = cleanRequired(organizationName, "Enter your organization name.");
            address = address == null ? "" : address.trim();
            if (organizationName.length() > 100 || address.length() > 200) {
                throw new IllegalArgumentException("Organization details exceed the field limits.");
            }
        }

        String passwordHash = PasswordUtil.hash(password);
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement check = con.prepareStatement("SELECT user_id FROM Users WHERE email=?")) {
                    check.setString(1, email);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next()) throw new IllegalArgumentException("That email is already registered.");
                    }
                }
                if (role.equals("VOLUNTEER") && !organizationExists(con, organizationId)) {
                    throw new IllegalArgumentException("Choose an existing organization.");
                }

                int userId;
                try (PreparedStatement insert = con.prepareStatement(
                        "INSERT INTO Users(name,email,phone,role,password_hash) VALUES (?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    insert.setString(1, name);
                    insert.setString(2, email.toLowerCase(Locale.ROOT));
                    insert.setString(3, phone);
                    insert.setString(4, role);
                    insert.setString(5, passwordHash);
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Could not create the account.");
                        userId = keys.getInt(1);
                    }
                }

                int volunteerId = 0;
                int orgId = 0;
                if (role.equals("NGO")) {
                    try (PreparedStatement insert = con.prepareStatement(
                            "INSERT INTO Organizations(user_id,org_name,address) VALUES (?,?,?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        insert.setInt(1, userId);
                        insert.setString(2, organizationName);
                        insert.setString(3, address);
                        insert.executeUpdate();
                        try (ResultSet keys = insert.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Could not create the organization profile.");
                            orgId = keys.getInt(1);
                        }
                    }
                } else if (role.equals("VOLUNTEER")) {
                    orgId = organizationId;
                    try (PreparedStatement insert = con.prepareStatement(
                            "INSERT INTO Volunteers(user_id,org_id) VALUES (?,?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        insert.setInt(1, userId);
                        insert.setInt(2, orgId);
                        insert.executeUpdate();
                        try (ResultSet keys = insert.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Could not create the volunteer profile.");
                            volunteerId = keys.getInt(1);
                        }
                    }
                }
                con.commit();
                return new UserAccount(userId, name, email.toLowerCase(Locale.ROOT),
                        role, volunteerId, orgId);
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    public UserAccount authenticate(String email, String password)
            throws SQLException, GeneralSecurityException {
        String sql = "SELECT u.user_id,u.name,u.email,u.role,u.password_hash,"
                + "COALESCE(v.volunteer_id,0) AS volunteer_id,"
                + "COALESCE(v.org_id,o.org_id,0) AS org_id "
                + "FROM Users u LEFT JOIN Volunteers v ON v.user_id=u.user_id "
                + "LEFT JOIN Organizations o ON o.user_id=u.user_id WHERE LOWER(u.email)=LOWER(?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email == null ? "" : email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !PasswordUtil.verify(password == null ? "" : password,
                        rs.getString("password_hash"))) return null;
                return new UserAccount(rs.getInt("user_id"), rs.getString("name"),
                        rs.getString("email"), rs.getString("role"),
                        rs.getInt("volunteer_id"), rs.getInt("org_id"));
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public List<OrganizationOption> getOrganizations() throws SQLException {
        List<OrganizationOption> organizations = new ArrayList<>();
        String sql = "SELECT org_id,org_name FROM Organizations ORDER BY org_name";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) organizations.add(new OrganizationOption(
                    rs.getInt("org_id"), rs.getString("org_name")));
        }
        return organizations;
    }

    private static boolean organizationExists(Connection con, int organizationId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT 1 FROM Organizations WHERE org_id=?")) {
            ps.setInt(1, organizationId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private static String cleanRequired(String value, String message) {
        String cleaned = value == null ? "" : value.trim();
        if (cleaned.isEmpty()) throw new IllegalArgumentException(message);
        return cleaned;
    }
}
