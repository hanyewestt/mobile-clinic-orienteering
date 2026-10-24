/*
 * Compile from the project root:
 * javac -d out $(find src test -name '*.java' -print)
 *
 * Run this test:
 * java -cp out algorithms.LazyGreedySolverSmokeTest
 */
package algorithms;

import coverage.CoverageBuilder;
import coverage.CoverageIndex;
import java.util.List;
import model.CandidateStop;
import model.Coordinate;
import model.DemandTown;
import model.ProblemInstance;

/** Small dependency-free check for both lazy and full versions of Approach A. */
public final class LazyGreedySolverSmokeTest {
    private LazyGreedySolverSmokeTest() {
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
                "small approach A test",
                new Coordinate(0.0, 0.0),
                candidates,
                demandTowns,
                10.0,
                50.0);
        CoverageIndex coverageIndex = CoverageBuilder.build(candidates, demandTowns, instance.serviceRadiusKm());

        ApproachAResult lazy = new LazyGreedySolver(ScoreRefreshMode.LAZY, 3)
                .solveDetailed(instance, coverageIndex);
        ApproachAResult full = new LazyGreedySolver(ScoreRefreshMode.FULL, 3)
                .solveDetailed(instance, coverageIndex);

        verifyResult(lazy, "lazy");
        verifyResult(full, "full");
        check(lazy.heapPops() > 0, "Lazy mode must use heap pops.");
        check(full.heapPops() == 0, "Full mode must not use a heap.");

        ApproachAResult traced = new LazyGreedySolver(ScoreRefreshMode.LAZY, 3, true)
                .solveDetailed(instance, coverageIndex);
        check(traced.decisionTrace().size() == 2, "Trace mode must record each accepted stop.");
        DecisionTrace firstDecision = traced.decisionTrace().get(0);
        check(firstDecision.chosenCandidateStopId() == 0,
                "The first trace should identify the depot stop as the winner.");
        check(firstDecision.marginalPopulation() == 300,
                "The first trace must preserve the chosen stop's marginal population.");
        check(firstDecision.populationAfter() == 300,
                "Trace coverage totals must reflect the accepted stop.");

        System.out.println("Approach A smoke test passed.");
    }

    private static void verifyResult(ApproachAResult result, String mode) {
        check(result.runResult().populationCovered() == 700,
                mode + " mode should cover depot and east populations only.");
        check(result.runResult().townsCovered() == 3,
                mode + " mode should cover three towns.");
        check(result.runResult().route().stopIds().size() == 2,
                mode + " mode should select two affordable stops.");
        check(result.runResult().route().totalDistanceKm() <= 50.0,
                mode + " mode must stay within budget.");
        check(result.deferredStops() == 1,
                mode + " mode should defer the far stop.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
