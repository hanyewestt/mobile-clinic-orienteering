package algorithms;

import coverage.CoverageBuilder;
import coverage.CoverageIndex;
import coverage.CoverageTracker;
import geometry.Haversine;
import java.util.HashSet;
import java.util.List;
import model.CandidateStop;
import model.Coordinate;
import model.DemandTown;
import model.ProblemInstance;
import model.RunResult;
import routing.CheapestInsertion;
import routing.Insertion;
import routing.RouteCalculator;
import trace.StepRecorder;

/** Deterministic, dependency-free regression checks for both approaches. */
public final class CorrectnessRegressionTest {
    private static final double EPS = 1e-6;
    private static int checksPassed = 0;
    private static final Coordinate DEPOT = new Coordinate(0, 0);
    private static final List<CandidateStop> STOPS = List.of(
            new CandidateStop(0, 100, "A", new Coordinate(0, 0), "PPL"),
            new CandidateStop(1, 101, "B", new Coordinate(0, .10), "PPL"),
            new CandidateStop(2, 102, "C", new Coordinate(.10, .08), "PPL"));
    private static final List<DemandTown> TOWNS = List.of(
            new DemandTown(0, 200, "Town 1", new Coordinate(0, 0), "PPL", 1000),
            new DemandTown(1, 201, "Town 2", new Coordinate(0, .05), "PPL", 500),
            new DemandTown(2, 202, "Town 3", new Coordinate(0, .15), "PPL", 800));

    public static void main(String[] args) {
        CoverageIndex index = CoverageBuilder.build(STOPS, TOWNS, 8.0);
        section("1. COVERAGE TESTS");
        testCoverage(index);
        section("2. CHEAPEST INSERTION AND BUDGET TESTS");
        testInsertion();
        section("3. ALGORITHM A AND B FINAL RESULTS");
        testBothSolvers(index);
        System.out.printf("%nRESULT: PASS - %d/%d checks passed.%n", checksPassed, checksPassed);
    }

    private static void testCoverage(CoverageIndex index) {
        CoverageTracker tracker = new CoverageTracker(index, TOWNS);
        eq(tracker.marginalPopulationGain(0), 1500, "A initial gain");
        eq(tracker.marginalPopulationGain(1), 1300, "B initial gain");
        eq(tracker.applyStop(0).newlyCoveredPopulation(), 1500, "A applied gain");
        eq(tracker.marginalPopulationGain(1), 800, "B after A");
        eq(tracker.applyStop(1).newlyCoveredPopulation(), 800, "B applied after A");
        eq(tracker.coveredPopulation(), 2300, "A then B total");
        eq(tracker.marginalPopulationGain(0), 0, "A fully redundant");
        eq(tracker.marginalPopulationGain(1), 0, "B fully redundant");
        CoverageTracker reverse = new CoverageTracker(index, TOWNS);
        reverse.applyStop(1);
        reverse.applyStop(0);
        eq(reverse.coveredPopulation(), 2300, "B then A total");
    }

    private static void testInsertion() {
        List<Integer> route = List.of(0, 1);
        double before = RouteCalculator.closedRouteLength(route, DEPOT, STOPS);
        double min = Double.POSITIVE_INFINITY;
        int minPosition = -1;
        for (int pos = 0; pos <= route.size(); pos++) {
            Coordinate prev = pos == 0 ? DEPOT : STOPS.get(route.get(pos - 1)).coordinate();
            Coordinate next = pos == route.size() ? DEPOT : STOPS.get(route.get(pos)).coordinate();
            Coordinate c = STOPS.get(2).coordinate();
            double expectedDelta = Haversine.distanceKm(prev, c)
                    + Haversine.distanceKm(c, next) - Haversine.distanceKm(prev, next);
            double actualDelta = RouteCalculator.insertionCost(route, 2, pos, DEPOT, STOPS);
            approx(actualDelta, expectedDelta, "edge insertion formula at position " + pos);
            List<Integer> expanded = RouteCalculator.insertStop(route, 2, pos);
            approx(RouteCalculator.closedRouteLength(expanded, DEPOT, STOPS) - before,
                    actualDelta, "whole-route delta at position " + pos);
            if (actualDelta < min) {
                min = actualDelta;
                minPosition = pos;
            }
        }
        Insertion best = CheapestInsertion.findBest(route, 2, DEPOT, STOPS);
        eq(best.position(), minPosition, "cheapest insertion position");
        approx(best.addedDistanceKm(), min, "cheapest insertion cost");
        boolean allowed = RouteCalculator.fitsBudget(before, best.addedDistanceKm(), before + min - .01);
        check(!allowed, "overbudget insertion rejected");
        eq(route.size(), 2, "original route remains unchanged");
        boolean duplicateRejected = false;
        try { RouteCalculator.insertStop(route, 0, 1); }
        catch (IllegalArgumentException ex) { duplicateRejected = true; }
        check(duplicateRejected, "duplicate stop insertion rejected");
    }

    private static void testBothSolvers(CoverageIndex index) {
        ProblemInstance instance = new ProblemInstance("toy", DEPOT, STOPS, TOWNS, 8, 55);
        ApproachAResult a = new LazyGreedySolver(3, true).solveDetailed(instance, index);
        section("Algorithm A");
        printSolution(a.runResult());
        checkFinal(a.runResult(), instance, index, "A");
        for (DecisionTrace decision : a.decisionTrace()) {
            check(decision.marginalPopulation() > 0, "A: every accepted stop adds population");
            check(decision.routeLengthAfterKm() <= instance.travelBudgetKm() + EPS,
                    "A: every accepted insertion fits budget");
            eq(decision.populationAfter() - decision.populationBefore(),
                    decision.marginalPopulation(), "A: trace marginal gain consistent");
        }
        GridClusterResult b = new GridClusterSolver(3, false, StepRecorder.NONE, true)
                .solveDetailed(instance, index);
        section("Algorithm B");
        printSolution(b.runResult());
        checkFinal(b.runResult(), instance, index, "B");
    }

    private static void checkFinal(RunResult result, ProblemInstance instance,
            CoverageIndex index, String label) {
        List<Integer> ids = result.route().stopIds();
        check(new HashSet<>(ids).size() == ids.size(), label + ": no duplicate stops");
        check(result.route().totalDistanceKm() <= instance.travelBudgetKm() + EPS,
                label + ": within travel budget");
        double independentDistance = 0;
        Coordinate prev = instance.depot();
        CoverageTracker scratch = new CoverageTracker(index, instance.demandTowns());
        for (int id : ids) {
            check(id >= 0 && id < STOPS.size(), label + ": valid stop ID");
            independentDistance += Haversine.distanceKm(prev, STOPS.get(id).coordinate());
            prev = STOPS.get(id).coordinate();
            scratch.applyStop(id);
        }
        if (!ids.isEmpty()) independentDistance += Haversine.distanceKm(prev, instance.depot());
        approx(result.route().totalDistanceKm(), independentDistance,
                label + ": sum of edges includes return to depot");
        eq(result.populationCovered(), scratch.coveredPopulation(),
                label + ": independently recomputed covered population");
        eq(result.townsCovered(), scratch.servedTownCount(),
                label + ": independently recomputed town count");
    }

    private static void printSolution(RunResult result) {
        System.out.println("Selected stop IDs: " + result.route().stopIds());
        System.out.printf("Reported route length: %.3f km%n", result.route().totalDistanceKm());
        System.out.println("Reported covered population: " + result.populationCovered());
        System.out.println("Reported towns covered: " + result.townsCovered());
    }

    private static void section(String title) {
        System.out.println("\n=== " + title + " ===");
    }

    private static void eq(long actual, long expected, String msg) {
        boolean passed = actual == expected;
        System.out.printf("%s | %s | expected: %d | actual: %d%n",
                passed ? "PASS" : "FAIL", msg, expected, actual);
        if (!passed) throw new AssertionError(msg + " expected=" + expected + " actual=" + actual);
        checksPassed++;
    }

    private static void approx(double actual, double expected, String msg) {
        boolean passed = Math.abs(actual - expected) <= EPS;
        System.out.printf("%s | %s | expected: %.6f | actual: %.6f%n",
                passed ? "PASS" : "FAIL", msg, expected, actual);
        if (!passed) throw new AssertionError(msg + " expected=" + expected + " actual=" + actual);
        checksPassed++;
    }

    private static void check(boolean condition, String msg) {
        System.out.printf("%s | %s | expected: true | actual: %s%n",
                condition ? "PASS" : "FAIL", msg, condition);
        if (!condition) throw new AssertionError(msg);
        checksPassed++;
    }
}
