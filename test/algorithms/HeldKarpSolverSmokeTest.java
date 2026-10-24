/*
 * Compile from the project root:
 * javac -d out $(find src test -name '*.java' -print)
 *
 * Run this test:
 * java -cp out algorithms.HeldKarpSolverSmokeTest
 */
package algorithms;

import coverage.CoverageBuilder;
import coverage.CoverageIndex;
import java.util.List;
import model.CandidateStop;
import model.Coordinate;
import model.DemandTown;
import model.ProblemInstance;

/** Checks the exact baseline picks the best budget-feasible coverage subset. */
public final class HeldKarpSolverSmokeTest {
    private HeldKarpSolverSmokeTest() {
    }

    public static void main(String[] args) {
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
                "small exact test",
                new Coordinate(0.0, 0.0),
                candidates,
                demandTowns,
                10.0,
                50.0);
        CoverageIndex coverageIndex = CoverageBuilder.build(candidates, demandTowns, instance.serviceRadiusKm());

        ExactBaselineResult result = new HeldKarpSolver().solveDetailed(instance, coverageIndex);

        check(result.runResult().populationCovered() == 700,
                "Exact solver should choose the two affordable stops for 700 people.");
        check(result.runResult().townsCovered() == 3, "Exact solver should serve three towns.");
        check(result.runResult().route().stopIds().size() == 2, "Exact solver should select two stops.");
        check(result.runResult().route().totalDistanceKm() <= 50.0, "Exact route must respect the budget.");
        check(result.subsetsEvaluated() == 7, "Three candidates create seven non-empty subsets.");

        System.out.println("Held-Karp smoke test passed.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
