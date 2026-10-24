package routing;

import java.util.List;
import java.util.Objects;
import model.CandidateStop;
import model.Coordinate;

/** Finds the cheapest position for adding one candidate stop to a closed route. */
public final class CheapestInsertion {
    private CheapestInsertion() {
    }

    public static Insertion findBest(
            List<Integer> routeStopIds,
            int stopId,
            Coordinate depot,
            List<CandidateStop> candidateStops) {
        Objects.requireNonNull(routeStopIds, "routeStopIds");
        if (routeStopIds.contains(stopId)) {
            throw new IllegalArgumentException("A route cannot visit the same candidate stop twice: " + stopId);
        }

        Insertion bestInsertion = null;
        for (int position = 0; position <= routeStopIds.size(); position++) {
            double addedDistanceKm = RouteCalculator.insertionCost(
                    routeStopIds, stopId, position, depot, candidateStops);
            if (bestInsertion == null || addedDistanceKm < bestInsertion.addedDistanceKm()) {
                bestInsertion = new Insertion(position, addedDistanceKm);
            }
        }
        return bestInsertion;
    }
}
