package routing;

import model.Route;

/** The route and work performed by a capped 2-opt polish. */
public record TwoOptResult(Route route, int completedPasses, int improvements) {
    public TwoOptResult {
        if (completedPasses < 0 || improvements < 0) {
            throw new IllegalArgumentException("2-opt counts must not be negative.");
        }
    }
}
