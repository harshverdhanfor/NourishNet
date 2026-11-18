package nourishnet;

public class UserAccount {
    private final int userId;
    private final String name;
    private final String email;
    private final String role;
    private final int volunteerId;
    private final int orgId;

    public UserAccount(int userId, String name, String email, String role,
                       int volunteerId, int orgId) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.volunteerId = volunteerId;
        this.orgId = orgId;
    }

    public int getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public int getVolunteerId() { return volunteerId; }
    public int getOrgId() { return orgId; }
}
