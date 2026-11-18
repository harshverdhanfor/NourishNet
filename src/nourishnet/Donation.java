package nourishnet;

public class Donation {
    private int id, donorId, quantity;
    private String foodType, location, pickupTime, bestBy, status;

    public Donation(int id, int donorId, String foodType, int quantity,
                    String location, String pickupTime, String bestBy, String status) {
        this.id = id; this.donorId = donorId; this.foodType = foodType;
        this.quantity = quantity; this.location = location;
        this.pickupTime = pickupTime; this.bestBy = bestBy; this.status = status;
    }
    public int getId() { return id; }
    public int getDonorId() { return donorId; }
    public int getQuantity() { return quantity; }
    public String getFoodType() { return foodType; }
    public String getLocation() { return location; }
    public String getPickupTime() { return pickupTime; }
    public String getBestBy() { return bestBy; }
    public String getStatus() { return status; }
}
