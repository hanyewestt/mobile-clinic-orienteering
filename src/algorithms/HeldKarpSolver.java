package algorithms;

import coverage.CoverageIndex;
import coverage.CoverageTracker;
import geometry.Haversine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import model.CandidateStop;
import model.ProblemInstance;
import model.Route;
import model.RunResult;
import routing.RouteCalculator;

/**
 * Exact Held-Karp baseline for very small instances only. It finds the shortest
 * closed route for every subset, then chooses the budget-feasible subset with
 * the greatest service-radius coverage.
 */
public final class HeldKarpSolver implements RoutingSolver {
    public static final int DEFAULT_CANDIDATE_STOP_LIMIT = 15;
    private static final double DISTANCE_EPSILON = 1.0e-9;

    private final int candidateStopLimit;

    public HeldKarpSolver() {
        this(DEFAULT_CANDIDATE_STOP_LIMIT);
    }

    public HeldKarpSolver(int candidateStopLimit) {
        if (candidateStopLimit < 1 || candidateStopLimit > 20) {
            throw new IllegalArgumentException("Candidate-stop limit must be between 1 and 20.");
        }
        this.candidateStopLimit = candidateStopLimit;
    }

    @Override
    public RunResult solve(ProblemInstance instance, CoverageIndex coverageIndex) {
        return solveDetailed(instance, coverageIndex).runResult();
    }

    public ExactBaselineResult solveDetailed(ProblemInstance instance, CoverageIndex coverageIndex) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(coverageIndex, "coverageIndex");
        validateInput(instance, coverageIndex);

        long startedAtNanos = System.nanoTime();
        int candidateCount = instance.candidateStops().size();
        int subsetCount = 1 << candidateCount;
        double[][] minimumPathKm = createDistanceTable(subsetCount, candidateCount);
        int[][] parent = createParentTable(subsetCount, candidateCount);
        fillDynamicProgram(instance, minimumPathKm, parent);

        BitSet[] coverageBySubset = buildCoverageBySubset(instance, coverageIndex);
        long[] populationByTownId = populationByTownId(instance);
        BestRoute best = new BestRoute(0L, 0.0, 0, -1);

        for (int subset = 1; subset < subsetCount; subset++) {
            RouteEnd routeEnd = shortestClosedRouteForSubset(
                    subset, instance, minimumPathKm);
            if (routeEnd.lengthKm() > instance.travelBudgetKm() + DISTANCE_EPSILON) {
                continue;
            }

            long coveredPopulation = coveredPopulation(coverageBySubset[subset], populationByTownId);
            if (coveredPopulation > best.coveredPopulation()
                    || (coveredPopulation == best.coveredPopulation()
                    && routeEnd.lengthKm() < best.lengthKm() - DISTANCE_EPSILON)) {
                best = new BestRoute(coveredPopulation, routeEnd.lengthKm(), subset, routeEnd.lastStopId());
            }
        }

        List<Integer> stopIds = best.subset() == 0
                ? List.of()
                : reconstructRoute(best.subset(), best.lastStopId(), parent);
        Route route = RouteCalculator.createRoute(stopIds, instance.depot(), instance.candidateStops());
        CoverageTracker tracker = new CoverageTracker(coverageIndex, instance.demandTowns());
        for (int stopId : stopIds) {
            tracker.applyStop(stopId);
        }

        long runtimeMillis = (System.nanoTime() - startedAtNanos) / 1_000_000;
        RunResult runResult = new RunResult(
                "Exact baseline - Held-Karp",
                route,
                tracker.coveredPopulation(),
                tracker.servedTownCount(),
                runtimeMillis);
        return new ExactBaselineResult(runResult, subsetCount - 1L, candidateStopLimit);
    }

    private static void fillDynamicProgram(
            ProblemInstance instance,
            double[][] minimumPathKm,
            int[][] parent) {
        int candidateCount = instance.candidateStops().size();
        for (int stopId = 0; stopId < candidateCount; stopId++) {
            minimumPathKm[1 << stopId][stopId] = Haversine.distanceKm(
                    instance.depot(), instance.candidateStops().get(stopId).coordinate());
        }

        for (int subset = 1; subset < minimumPathKm.length; subset++) {
            for (int lastStopId = 0; lastStopId < candidateCount; lastStopId++) {
                if ((subset & (1 << lastStopId)) == 0) {
                    continue;
                }
                int previousSubset = subset ^ (1 << lastStopId);
                if (previousSubset == 0) {
                    continue;
                }

                for (int previousStopId = 0; previousStopId < candidateCount; previousStopId++) {
                    if ((previousSubset & (1 << previousStopId)) == 0) {
                        continue;
                    }
                    double priorLength = minimumPathKm[previousSubset][previousStopId];
                    if (Double.isInfinite(priorLength)) {
                        continue;
                    }
                    double candidateLength = priorLength + Haversine.distanceKm(
                            instance.candidateStops().get(previousStopId).coordinate(),
                            instance.candidateStops().get(lastStopId).coordinate());
                    if (candidateLength < minimumPathKm[subset][lastStopId]) {
                        minimumPathKm[subset][lastStopId] = candidateLength;
                        parent[subset][lastStopId] = previousStopId;
                    }
                }
            }
        }
    }

    private static RouteEnd shortestClosedRouteForSubset(
            int subset,
            ProblemInstance instance,
            double[][] minimumPathKm) {
        double shortestLengthKm = Double.POSITIVE_INFINITY;
        int bestLastStopId = -1;
        for (int lastStopId = 0; lastStopId < instance.candidateStops().size(); lastStopId++) {
            if ((subset & (1 << lastStopId)) == 0) {
                continue;
            }
            double pathLength = minimumPathKm[subset][lastStopId];
            double closedLength = pathLength + Haversine.distanceKm(
                    instance.candidateStops().get(lastStopId).coordinate(), instance.depot());
            if (closedLength < shortestLengthKm) {
                shortestLengthKm = closedLength;
                bestLastStopId = lastStopId;
            }
        }
        return new RouteEnd(shortestLengthKm, bestLastStopId);
    }

    private static BitSet[] buildCoverageBySubset(ProblemInstance instance, CoverageIndex coverageIndex) {
        int subsetCount = 1 << instance.candidateStops().size();
        BitSet[] coverageBySubset = new BitSet[subsetCount];
        coverageBySubset[0] = new BitSet(instance.demandTowns().size());

        for (int subset = 1; subset < subsetCount; subset++) {
            int lastStopId = Integer.numberOfTrailingZeros(subset);
            int previousSubset = subset ^ (1 << lastStopId);
            BitSet coverage = (BitSet) coverageBySubset[previousSubset].clone();
            int coveredTownCount = coverageIndex.coveredTownCount(lastStopId);
            for (int position = 0; position < coveredTownCount; position++) {
                coverage.set(coverageIndex.coveredTownIdAt(lastStopId, position));
            }
            coverageBySubset[subset] = coverage;
        }
        return coverageBySubset;
    }

    private static long[] populationByTownId(ProblemInstance instance) {
        long[] populations = new long[instance.demandTowns().size()];
        for (int townId = 0; townId < populations.length; townId++) {
            populations[townId] = instance.demandTowns().get(townId).population();
        }
        return populations;
    }

    private static long coveredPopulation(BitSet coveredTowns, long[] populationByTownId) {
        long total = 0;
        for (int townId = coveredTowns.nextSetBit(0); townId >= 0; townId = coveredTowns.nextSetBit(townId + 1)) {
            total += populationByTownId[townId];
        }
        return total;
    }

    private static List<Integer> reconstructRoute(int subset, int lastStopId, int[][] parent) {
        List<Integer> reverseRoute = new ArrayList<>();
        int remainingSubset = subset;
        int currentStopId = lastStopId;
        while (currentStopId >= 0) {
            reverseRoute.add(currentStopId);
            int previousStopId = parent[remainingSubset][currentStopId];
            remainingSubset ^= 1 << currentStopId;
            currentStopId = previousStopId;
        }
        List<Integer> route = new ArrayList<>(reverseRoute.size());
        for (int index = reverseRoute.size() - 1; index >= 0; index--) {
            route.add(reverseRoute.get(index));
        }
        return List.copyOf(route);
    }

    private static double[][] createDistanceTable(int subsetCount, int candidateCount) {
        double[][] table = new double[subsetCount][candidateCount];
        for (double[] row : table) {
            Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        return table;
    }

    private static int[][] createParentTable(int subsetCount, int candidateCount) {
        int[][] table = new int[subsetCount][candidateCount];
        for (int[] row : table) {
            Arrays.fill(row, -1);
        }
        return table;
    }

    private void validateInput(ProblemInstance instance, CoverageIndex coverageIndex) {
        int candidateCount = instance.candidateStops().size();
        if (candidateCount > candidateStopLimit) {
            throw new IllegalArgumentException(
                    "Held-Karp supports at most " + candidateStopLimit + " candidate stops; received " + candidateCount);
        }
        if (coverageIndex.candidateStopCount() != candidateCount
                || coverageIndex.demandTownCount() != instance.demandTowns().size()) {
            throw new IllegalArgumentException("Coverage index does not match the problem instance.");
        }
    }

    private record RouteEnd(double lengthKm, int lastStopId) {
    }

    private record BestRoute(long coveredPopulation, double lengthKm, int subset, int lastStopId) {
    }
}
