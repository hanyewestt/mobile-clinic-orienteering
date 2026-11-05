package routing;

import geometry.Haversine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import model.CandidateStop;
import model.Coordinate;
import model.Route;

/** Improves a closed route through bounded 2-opt edge swaps. */
public final class TwoOptPolisher {
    private static final double IMPROVEMENT_EPSILON = 1.0e-9;

    private TwoOptPolisher() {
    }

    /** Observes each applied 2-opt move; used for step traces only. */
    @FunctionalInterface
    public interface SwapListener {
        void onSwap(List<Integer> stopIdsAfterSwap, int first, int second, double changeKm, double lengthKm);
    }

    public static TwoOptResult polish(
            Route initialRoute,
            Coordinate depot,
            List<CandidateStop> candidateStops,
            int maximumPasses) {
        return polish(initialRoute, depot, candidateStops, maximumPasses, null);
    }

    public static TwoOptResult polish(
            Route initialRoute,
            Coordinate depot,
            List<CandidateStop> candidateStops,
            int maximumPasses,
            SwapListener swapListener) {
        Objects.requireNonNull(initialRoute, "initialRoute");
        Objects.requireNonNull(depot, "depot");
        if (maximumPasses < 0) {
            throw new IllegalArgumentException("Maximum pass count must not be negative.");
        }

        List<Integer> stopIds = new ArrayList<>(initialRoute.stopIds());
        double currentLengthKm = RouteCalculator.closedRouteLength(stopIds, depot, candidateStops);
        int completedPasses = 0;
        int improvements = 0;

        while (completedPasses < maximumPasses) {
            completedPasses++;
            boolean improvedThisPass = false;

            for (int first = 0; first < stopIds.size() - 1 && !improvedThisPass; first++) {
                for (int second = first + 1; second < stopIds.size(); second++) {
                    double changeKm = swapDistanceChange(stopIds, first, second, depot, candidateStops);
                    if (changeKm < -IMPROVEMENT_EPSILON) {
                        Collections.reverse(stopIds.subList(first, second + 1));
                        currentLengthKm += changeKm;
                        improvements++;
                        if (swapListener != null) {
                            swapListener.onSwap(List.copyOf(stopIds), first, second, changeKm, currentLengthKm);
                        }
                        improvedThisPass = true;
                        break;
                    }
                }
            }

            if (!improvedThisPass) {
                break;
            }
        }

        Route polishedRoute = new Route(stopIds, Math.max(0.0, currentLengthKm));
        return new TwoOptResult(polishedRoute, completedPasses, improvements);
    }

    private static double swapDistanceChange(
            List<Integer> stopIds,
            int first,
            int second,
            Coordinate depot,
            List<CandidateStop> candidateStops) {
        Coordinate beforeFirst = first == 0
                ? depot
                : RouteCalculator.coordinateForStopId(stopIds.get(first - 1), candidateStops);
        Coordinate firstStop = RouteCalculator.coordinateForStopId(stopIds.get(first), candidateStops);
        Coordinate secondStop = RouteCalculator.coordinateForStopId(stopIds.get(second), candidateStops);
        Coordinate afterSecond = second == stopIds.size() - 1
                ? depot
                : RouteCalculator.coordinateForStopId(stopIds.get(second + 1), candidateStops);

        double removedEdgesKm = Haversine.distanceKm(beforeFirst, firstStop)
                + Haversine.distanceKm(secondStop, afterSecond);
        double addedEdgesKm = Haversine.distanceKm(beforeFirst, secondStop)
                + Haversine.distanceKm(firstStop, afterSecond);
        return addedEdgesKm - removedEdgesKm;
    }
}
