package routing;

import java.util.List;
import model.CandidateStop;
import model.Coordinate;
import model.Route;

/** Small dependency-free check for route length, insertion, budget and 2-opt behaviour. */
public final class RoutingSmokeTest {
    private static final Coordinate DEPOT = new Coordinate(0.0, 0.0);
    private static final List<CandidateStop> STOPS = List.of(
            new CandidateStop(0, 1, "East", new Coordinate(0.0, 0.10), "PPL"),
            new CandidateStop(1, 2, "Further East", new Coordinate(0.0, 0.20), "PPL"),
            new CandidateStop(2, 3, "North East", new Coordinate(0.10, 0.20), "PPL"));

    private RoutingSmokeTest() {
    }

    public static void main(String[] args) {
        Route firstRoute = RouteCalculator.createRoute(List.of(0), DEPOT, STOPS);
        check(firstRoute.totalDistanceKm() > 22.0 && firstRoute.totalDistanceKm() < 23.0,
                "A 0.1 degree equatorial out-and-back route should be about 22.2 km.");

        Insertion insertion = CheapestInsertion.findBest(firstRoute.stopIds(), 1, DEPOT, STOPS);
        check(insertion.addedDistanceKm() > 22.0 && insertion.addedDistanceKm() < 23.0,
                "Adding the further east stop should add about 22.2 km.");
        check(RouteCalculator.fitsBudget(firstRoute.totalDistanceKm(), insertion.addedDistanceKm(), 45.0),
                "The insertion should fit in a 45 km budget.");
        check(!RouteCalculator.fitsBudget(firstRoute.totalDistanceKm(), insertion.addedDistanceKm(), 44.0),
                "The insertion should not fit in a 44 km budget.");

        Route nonOptimalRoute = RouteCalculator.createRoute(List.of(0, 2, 1), DEPOT, STOPS);
        TwoOptResult polished = TwoOptPolisher.polish(nonOptimalRoute, DEPOT, STOPS, 4);
        check(polished.route().totalDistanceKm() <= nonOptimalRoute.totalDistanceKm(),
                "2-opt must not make the route longer.");
        check(polished.route().stopIds().size() == 3, "2-opt must preserve all stops.");

        System.out.println("Routing smoke test passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
