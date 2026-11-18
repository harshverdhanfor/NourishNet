package nourishnet;

public class OrganizationReport {
    private final int assigned;
    private final int delivered;
    private final int mealsDelivered;

    public OrganizationReport(int assigned, int delivered, int mealsDelivered) {
        this.assigned = assigned;
        this.delivered = delivered;
        this.mealsDelivered = mealsDelivered;
    }

    public int getAssigned() { return assigned; }
    public int getDelivered() { return delivered; }
    public int getMealsDelivered() { return mealsDelivered; }
}
