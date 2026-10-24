package routing;

/** The best position and extra distance for inserting one stop into a route. */
public record Insertion(int position, double addedDistanceKm) {
    public Insertion {
        if (position < 0) {
            throw new IllegalArgumentException("Insertion position must not be negative.");
        }
        if (addedDistanceKm < 0.0) {
            throw new IllegalArgumentException("Added distance must not be negative.");
        }
    }
}
