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
import coverage.CoverageTracker;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import model.CandidateStop;
import model.Coordinate;
import model.DemandTown;
import model.ProblemInstance;
import model.RunResult;
import routing.RouteCalculator;
import trace.StepRecorder;

/** Small dependency-free checks for Approach B, including exactness of the 3x3 local update. */
public final class GridClusterSolverSmokeTest {
    private GridClusterSolverSmokeTest() {
    }

    public static void main(String[] args) {
        checkHandMadeInstance();
        for (double radiusKm : new double[] {3.0, 10.0, 25.0}) {
            checkLocalUpdateIsExact(radiusKm);
        }
        System.out.println("Approach B smoke test passed.");
    }

    private static void checkHandMadeInstance() {
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
        CoverageIndex coverageIndex = CoverageBuilder.build(candidates, demandTowns, instance.serviceRadiusKm());

        GridClusterResult pruned = new GridClusterSolver(3, true, StepRecorder.NONE, true)
                .solveDetailed(instance, coverageIndex);
        check(pruned.prunedSites() == 1, "The far stop's solo round trip exceeds the budget, so it is pruned.");
        check(pruned.anchorsSelected() == 2, "Two reachable anchors cover every reachable town.");
        verifyResult(pruned.runResult(), instance, coverageIndex, "pruned");

        GridClusterResult unpruned = new GridClusterSolver(3, false, StepRecorder.NONE, true)
                .solveDetailed(instance, coverageIndex);
        check(unpruned.anchorsSelected() == 3, "Without pruning the far stop is selected for its coverage.");
        check(unpruned.anchorsSkipped() == 1, "The far stop is then skipped at routing time.");
        verifyResult(unpruned.runResult(), instance, coverageIndex, "unpruned");
    }

    private static void verifyResult(
            RunResult result, ProblemInstance instance, CoverageIndex coverageIndex, String mode) {
        check(result.populationCovered() == 700, mode + ": should cover depot and east populations only.");
        check(result.townsCovered() == 3, mode + ": should cover three towns.");
        check(result.route().totalDistanceKm() <= instance.travelBudgetKm(), mode + ": must stay within budget.");
        checkAgainstScratch(result, instance, coverageIndex, mode);
    }

    /**
     * Random sites near 6 degrees north, where the projection error is largest,
     * with a self-check after every pick: each cell's stored best gain and each
     * popped gain must equal a brute-force recomputation over all sites.
     */
    private static void checkLocalUpdateIsExact(double radiusKm) {
        Random random = new Random(201);
        List<CandidateStop> candidates = new ArrayList<>();
        for (int id = 0; id < 1_500; id++) {
            candidates.add(new CandidateStop(id, id + 1, "Site " + id,
                    new Coordinate(5.0 + 1.5 * random.nextDouble(), 100.0 + 1.5 * random.nextDouble()), "PPL"));
        }
        List<DemandTown> demandTowns = new ArrayList<>();
        for (int id = 0; id < 250; id++) {
            demandTowns.add(new DemandTown(id, 100_000 + id, "Town " + id,
                    new Coordinate(5.0 + 1.5 * random.nextDouble(), 100.0 + 1.5 * random.nextDouble()),
                    "PPL", 1 + random.nextInt(50_000)));
        }
        ProblemInstance instance = new ProblemInstance(
                "random approach B test", new Coordinate(5.75, 100.75), candidates, demandTowns, radiusKm, 400.0);
        CoverageIndex coverageIndex = CoverageBuilder.build(candidates, demandTowns, radiusKm);

        GridClusterResult result = new GridClusterSolver(3, false, StepRecorder.NONE, true)
                .solveDetailed(instance, coverageIndex);
        check(result.runResult().route().totalDistanceKm() <= instance.travelBudgetKm() + 1e-9,
                "r=" + radiusKm + ": route must stay within budget.");
        check(result.stalePops() > 0, "r=" + radiusKm + ": overlapping picks should leave stale heap entries.");
        checkAgainstScratch(result.runResult(), instance, coverageIndex, "r=" + radiusKm);
    }

    /** Reported coverage and length must match a recomputation from the final route alone. */
    private static void checkAgainstScratch(
            RunResult result, ProblemInstance instance, CoverageIndex coverageIndex, String label) {
        CoverageTracker tracker = new CoverageTracker(coverageIndex, instance.demandTowns());
        for (int stopId : result.route().stopIds()) {
            tracker.applyStop(stopId);
        }
        check(tracker.coveredPopulation() == result.populationCovered(), label + ": population must match scratch.");
        check(tracker.servedTownCount() == result.townsCovered(), label + ": town count must match scratch.");
        double lengthKm = RouteCalculator.closedRouteLength(
                result.route().stopIds(), instance.depot(), instance.candidateStops());
        check(Math.abs(lengthKm - result.route().totalDistanceKm()) < 1e-6, label + ": length must match scratch.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
