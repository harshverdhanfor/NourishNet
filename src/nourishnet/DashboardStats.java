package nourishnet;

public class DashboardStats {
    private final int totalDonations;
    private final int available;
    private final int accepted;
    private final int collected;
    private final int delivered;
    private final int mealsAvailable;

    public DashboardStats(int totalDonations, int available, int accepted,
                          int collected, int delivered, int mealsAvailable) {
        this.totalDonations = totalDonations;
        this.available = available;
        this.accepted = accepted;
        this.collected = collected;
        this.delivered = delivered;
        this.mealsAvailable = mealsAvailable;
    }

    public int getTotalDonations() { return totalDonations; }
    public int getAvailable() { return available; }
    public int getAccepted() { return accepted; }
    public int getCollected() { return collected; }
    public int getDelivered() { return delivered; }
    public int getMealsAvailable() { return mealsAvailable; }
}
