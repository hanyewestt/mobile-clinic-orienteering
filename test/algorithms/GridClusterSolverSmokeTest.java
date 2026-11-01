/*
 * Compile from the project root:
 * javac -d out $(find src test -name '*.java' -print)
 *
 * Run this test:
 * java -cp out algorithms.GridClusterSolverSmokeTest
 */
package algorithms;

import coverage.CoverageBuilder;
import coverage.CoverageIndex;
import geometry.Haversine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import model.CandidateStop;
import model.Coordinate;
import model.DemandTown;
import model.ProblemInstance;
import model.RunResult;

/** Dependency-free checks for Approach B on a tiny and a seeded random instance. */
public final class GridClusterSolverSmokeTest {
    private GridClusterSolverSmokeTest() {
    }

    public static void main(String[] args) {
        smallInstance();
        for (long seed = 1; seed <= 10; seed++) {
            randomInstance(seed);
        }
        System.out.println("Grid cluster solver smoke test passed.");
    }

    private static void smallInstance() {
        List<CandidateStop> candidates = List.of(
                new CandidateStop(0, 1, "Depot Stop", new Coordinate(0.0, 0.0), "PPL"),
                new CandidateStop(1, 2, "East Stop", new Coordinate(0.0, 0.20), "PPL"),
                new CandidateStop(2, 3, "Too Far Stop", new Coordinate(0.0, 0.40), "PPL"));
        List<DemandTown> demandTowns = List.of(
                new DemandTown(0, 10, "Depot Town", new Coordinate(0.0, 0.0), "PPL", 100),
                new DemandTown(1, 11, "Nearby Town", new Coordinate(0.0, 0.05), "PPL", 200),
                new DemandTown(2, 12, "East Town", new Coordinate(0.0, 0.20), "PPL", 400),
                new DemandTown(3, 13, "Far Town", new Coordinate(0.0, 0.40), "PPL", 1_000));
        ProblemInstance instance = new ProblemInstance(
                "small approach B test", new Coordinate(0.0, 0.0), candidates, demandTowns, 10.0, 50.0);
        CoverageIndex index = CoverageBuilder.build(candidates, demandTowns, instance.serviceRadiusKm());

        ApproachBResult result = new GridClusterSolver(3).solveDetailed(instance, index);
        // Same instance as the Approach A test: the far stop does not fit the 50 km budget.
        check(result.runResult().populationCovered() == 700, "Small instance should cover 700 people.");
        check(result.runResult().townsCovered() == 3, "Small instance should cover 3 towns.");
        check(result.runResult().route().stopIds().size() == 2, "Small instance should route 2 stops.");
        check(result.runResult().route().totalDistanceKm() <= 50.0, "Route must respect the budget.");
        check(result.cellSideKm() == 20.0, "Cell side must be 2r.");
    }

    private static void randomInstance(long seed) {
        Random random = new Random(seed);
        int stopCount = 400;
        int townCount = 60;
        List<CandidateStop> stops = new ArrayList<>();
        for (int id = 0; id < stopCount; id++) {
            stops.add(new CandidateStop(id, id + 1, "S" + id, randomPoint(random), "PPL"));
        }
        List<DemandTown> towns = new ArrayList<>();
        for (int id = 0; id < townCount; id++) {
            towns.add(new DemandTown(id, id + 1, "T" + id, randomPoint(random), "PPL", 1_000 + random.nextInt(50_000)));
        }
        double radiusKm = 5.0 + random.nextInt(20);
        double budgetKm = 100.0 + random.nextInt(400);
        ProblemInstance instance = new ProblemInstance(
                "random " + seed, new Coordinate(0.5, 0.5), stops, towns, radiusKm, budgetKm);
        CoverageIndex index = CoverageBuilder.build(stops, towns, radiusKm);

        RunResult result = new GridClusterSolver(3).solveDetailed(instance, index).runResult();

        check(result.route().totalDistanceKm() <= budgetKm + 1.0e-6, "Seed " + seed + ": budget exceeded.");
        Set<Integer> unique = new HashSet<>(result.route().stopIds());
        check(unique.size() == result.route().stopIds().size(), "Seed " + seed + ": duplicate stop in route.");

        // Independently recompute coverage so overlapping stops are never double counted.
        long population = 0;
        int covered = 0;
        for (DemandTown town : towns) {
            for (int stopId : result.route().stopIds()) {
                if (Haversine.distanceKm(stops.get(stopId).coordinate(), town.coordinate()) <= radiusKm) {
                    population += town.population();
                    covered++;
                    break;
                }
            }
        }
        check(population == result.populationCovered(), "Seed " + seed + ": population mismatch.");
        check(covered == result.townsCovered(), "Seed " + seed + ": town count mismatch.");
    }

    private static Coordinate randomPoint(Random random) {
        return new Coordinate(random.nextDouble() * 1.0, random.nextDouble() * 1.0);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
