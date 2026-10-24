package routing;

import geometry.Haversine;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import model.CandidateStop;
import model.Coordinate;
import model.Route;

/** Shared calculations for closed depot-to-depot routes. */
public final class RouteCalculator {
    private static final double DISTANCE_EPSILON = 1.0e-9;

    private RouteCalculator() {
    }

    public static Route createRoute(
            List<Integer> stopIds, Coordinate depot, List<CandidateStop> candidateStops) {
        return new Route(stopIds, closedRouteLength(stopIds, depot, candidateStops));
    }

    public static double closedRouteLength(
            List<Integer> stopIds, Coordinate depot, List<CandidateStop> candidateStops) {
        Objects.requireNonNull(stopIds, "stopIds");
        Objects.requireNonNull(depot, "depot");
        validateCandidateStops(candidateStops);

        if (stopIds.isEmpty()) {
            return 0.0;
        }

        double lengthKm = 0.0;
        Coordinate previous = depot;
        for (int stopId : stopIds) {
            Coordinate current = coordinateForStopId(stopId, candidateStops);
            lengthKm += Haversine.distanceKm(previous, current);
            previous = current;
        }
        lengthKm += Haversine.distanceKm(previous, depot);
        return lengthKm;
    }

    public static double insertionCost(
            List<Integer> routeStopIds,
            int stopId,
            int position,
            Coordinate depot,
            List<CandidateStop> candidateStops) {
        Objects.requireNonNull(routeStopIds, "routeStopIds");
        Objects.requireNonNull(depot, "depot");
        validateCandidateStops(candidateStops);
        if (position < 0 || position > routeStopIds.size()) {
            throw new IndexOutOfBoundsException("Insertion position out of range: " + position);
        }
        if (routeStopIds.contains(stopId)) {
            throw new IllegalArgumentException("A route cannot visit the same candidate stop twice: " + stopId);
        }

        Coordinate inserted = coordinateForStopId(stopId, candidateStops);
        Coordinate previous = position == 0
                ? depot
                : coordinateForStopId(routeStopIds.get(position - 1), candidateStops);
        Coordinate next = position == routeStopIds.size()
                ? depot
                : coordinateForStopId(routeStopIds.get(position), candidateStops);

        double addedDistanceKm = Haversine.distanceKm(previous, inserted)
                + Haversine.distanceKm(inserted, next)
                - Haversine.distanceKm(previous, next);
        return Math.max(0.0, addedDistanceKm);
    }

    public static List<Integer> insertStop(List<Integer> routeStopIds, int stopId, int position) {
        Objects.requireNonNull(routeStopIds, "routeStopIds");
        if (position < 0 || position > routeStopIds.size()) {
            throw new IndexOutOfBoundsException("Insertion position out of range: " + position);
        }
        if (routeStopIds.contains(stopId)) {
            throw new IllegalArgumentException("A route cannot visit the same candidate stop twice: " + stopId);
        }

        List<Integer> withInsertedStop = new ArrayList<>(routeStopIds);
        withInsertedStop.add(position, stopId);
        return List.copyOf(withInsertedStop);
    }

    public static boolean fitsBudget(double currentLengthKm, double additionalLengthKm, double budgetKm) {
        if (currentLengthKm < 0.0 || additionalLengthKm < 0.0 || budgetKm < 0.0) {
            throw new IllegalArgumentException("Distances and budget must not be negative.");
        }
        return currentLengthKm + additionalLengthKm <= budgetKm + DISTANCE_EPSILON;
    }

    static Coordinate coordinateForStopId(int stopId, List<CandidateStop> candidateStops) {
        if (stopId < 0 || stopId >= candidateStops.size()) {
            throw new IndexOutOfBoundsException("Candidate-stop id out of range: " + stopId);
        }
        return candidateStops.get(stopId).coordinate();
    }

    static void validateCandidateStops(List<CandidateStop> candidateStops) {
        Objects.requireNonNull(candidateStops, "candidateStops");
        for (int index = 0; index < candidateStops.size(); index++) {
            if (candidateStops.get(index).id() != index) {
                throw new IllegalArgumentException(
                        "Candidate-stop ids must be consecutive and match their list positions.");
            }
        }
    }
}
