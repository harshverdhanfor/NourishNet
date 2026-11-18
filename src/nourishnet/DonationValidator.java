package nourishnet;

public final class DonationValidator {
    private DonationValidator() { }

    public static void validateFoodType(String foodType) {
        if (foodType == null || foodType.isBlank()) {
            throw new IllegalArgumentException("Enter the food name.");
        }
        boolean hasLetter = foodType.codePoints().anyMatch(Character::isLetter);
        if (!hasLetter) {
            throw new IllegalArgumentException("Enter a valid food name with at least one letter.");
        }
        if (foodType.length() > 100) {
            throw new IllegalArgumentException("Food name must be 100 characters or fewer.");
        }
    }

    public static void validateDonation(String foodType, int quantity,
                                        String location, String pickupTime, String bestBy) {
        validateFoodType(foodType);
        if (quantity < 1 || quantity > 100000) {
            throw new IllegalArgumentException("Quantity must be between 1 and 100,000.");
        }
        validateLocation(location);
        validatePickupTime(pickupTime);
        validateBestBy(bestBy);
    }

    private static void validateLocation(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Enter a pickup location.");
        }
        if (location.length() > 200) {
            throw new IllegalArgumentException("Pickup location must be 200 characters or fewer.");
        }
        boolean hasLetter = location.codePoints().anyMatch(Character::isLetter);
        if (!hasLetter) {
            throw new IllegalArgumentException("Enter a location name or landmark, not only numbers.");
        }
    }

    private static void validatePickupTime(String pickupTime) {
        if (pickupTime == null || pickupTime.isBlank()) {
            throw new IllegalArgumentException("Enter a pickup time.");
        }
        if (pickupTime.length() > 30) {
            throw new IllegalArgumentException("Pickup time must be 30 characters or fewer.");
        }
        boolean hasLetterOrDigit = pickupTime.codePoints().anyMatch(Character::isLetterOrDigit);
        if (!hasLetterOrDigit) {
            throw new IllegalArgumentException("Enter a pickup time such as 6:00 PM or 18:00.");
        }
    }

    private static void validateBestBy(String bestBy) {
        if (bestBy == null || bestBy.isBlank()) return;
        if (bestBy.length() > 30) {
            throw new IllegalArgumentException("Best-before details must be 30 characters or fewer.");
        }
        boolean hasLetterOrDigit = bestBy.codePoints().anyMatch(Character::isLetterOrDigit);
        if (!hasLetterOrDigit) {
            throw new IllegalArgumentException("Enter valid best-before details or leave the field empty.");
        }
    }
}
