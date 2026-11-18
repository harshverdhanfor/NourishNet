package nourishnet;

public class UserNotification {
    private final int id;
    private final String message;
    private final String createdAt;
    private final boolean read;

    public UserNotification(int id, String message, String createdAt, boolean read) {
        this.id = id;
        this.message = message;
        this.createdAt = createdAt;
        this.read = read;
    }

    public int getId() { return id; }
    public String getMessage() { return message; }
    public String getCreatedAt() { return createdAt; }
    public boolean isRead() { return read; }
}
