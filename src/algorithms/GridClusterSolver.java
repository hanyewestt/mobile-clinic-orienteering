package algorithms;

import coverage.CoverageIndex;
import coverage.CoverageTracker;
import geometry.CoordinateProjector;
import geometry.PlanarPoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import model.CandidateStop;
import model.ProblemInstance;
import model.Route;
import model.RunResult;
import routing.CheapestInsertion;
import routing.Insertion;
import routing.RouteCalculator;
import routing.TwoOptPolisher;
import routing.TwoOptResult;

/**
 * Approach B: pick one coverage anchor per grid cell, ignoring route cost, and
 * only then build a route from the anchors with the shared routing tools.
 *
 * <p>Cells have side 2r, so anchors in non-adjacent cells are more than 2r
 * apart and cannot cover the same town. After an anchor is selected only its
 * eight neighbouring cells need their best anchor recomputed.
 */
public final class GridClusterSolver implements RoutingSolver {
    private static final double SCORE_EPSILON = 1.0e-9;
    private static final double MINIMUM_CELL_SIDE_KM = 0.001;

    private final int maximumTwoOptPasses;

    public GridClusterSolver(int maximumTwoOptPasses) {
        if (maximumTwoOptPasses < 0) {
            throw new IllegalArgumentException("Solver limits must not be negative.");
        }
        this.maximumTwoOptPasses = maximumTwoOptPasses;
    }

    @Override
    public RunResult solve(ProblemInstance instance, CoverageIndex coverageIndex) {
        return solveDetailed(instance, coverageIndex).runResult();
    }

    public ApproachBResult solveDetailed(ProblemInstance instance, CoverageIndex coverageIndex) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(coverageIndex, "coverageIndex");
        validateCoverageIndex(instance, coverageIndex);

        long startedAtNanos = System.nanoTime();
        double cellSideKm = Math.max(2.0 * instance.serviceRadiusKm(), MINIMUM_CELL_SIDE_KM);
        Grid grid = buildGrid(instance, cellSideKm);
        MutableStatistics statistics = new MutableStatistics();

        List<Integer> anchors = selectAnchors(instance, coverageIndex, grid, statistics);

        CoverageTracker routedCoverage = new CoverageTracker(coverageIndex, instance.demandTowns());
        boolean[] inRoute = new boolean[instance.candidateStops().size()];
        List<Integer> skipped = new ArrayList<>();
        Route route = RouteCalculator.createRoute(List.of(), instance.depot(), instance.candidateStops());

        for (int anchorId : anchors) {
            Insertion insertion = CheapestInsertion.findBest(
                    route.stopIds(), anchorId, instance.depot(), instance.candidateStops());
            if (RouteCalculator.fitsBudget(
                    route.totalDistanceKm(), insertion.addedDistanceKm(), instance.travelBudgetKm())) {
                route = insertStop(route, anchorId, insertion, instance, routedCoverage, inRoute);
            } else {
                skipped.add(anchorId);
            }
        }

        TwoOptResult polished = TwoOptPolisher.polish(
                route, instance.depot(), instance.candidateStops(), maximumTwoOptPasses);
        route = polished.route();

        int refilledStops = 0;
        while (true) {
            int bestId = -1;
            Insertion bestInsertion = null;
            double bestScore = 0.0;
            for (int skippedId : skipped) {
                if (inRoute[skippedId]) {
                    continue;
                }
                long gain = routedCoverage.marginalPopulationGain(skippedId);
                if (gain == 0) {
                    continue;
                }
                Insertion insertion = CheapestInsertion.findBest(
                        route.stopIds(), skippedId, instance.depot(), instance.candidateStops());
                if (!RouteCalculator.fitsBudget(
                        route.totalDistanceKm(), insertion.addedDistanceKm(), instance.travelBudgetKm())) {
                    continue;
                }
                double score = gain / (insertion.addedDistanceKm() + SCORE_EPSILON);
                if (bestInsertion == null || score > bestScore) {
                    bestId = skippedId;
                    bestInsertion = insertion;
                    bestScore = score;
                }
            }
            if (bestInsertion == null) {
                break;
            }
            route = insertStop(route, bestId, bestInsertion, instance, routedCoverage, inRoute);
            refilledStops++;
        }

        long runtimeMillis = (System.nanoTime() - startedAtNanos) / 1_000_000;
        RunResult runResult = new RunResult(
                "Approach B - grid clusters then route",
                route,
                routedCoverage.coveredPopulation(),
                routedCoverage.servedTownCount(),
                runtimeMillis);
        return new ApproachBResult(
                runResult,
                cellSideKm,
                grid.members.length,
                anchors.size(),
                statistics.cellRecomputations,
                statistics.heapPops,
                statistics.staleEntries,
                countUnrouted(skipped, inRoute),
                refilledStops,
                polished.improvements());
    }

    /** Selects anchors by coverage alone and returns them in selection order. */
    private static List<Integer> selectAnchors(
            ProblemInstance instance,
            CoverageIndex coverageIndex,
            Grid grid,
            MutableStatistics statistics) {
        CoverageTracker selectionCoverage = new CoverageTracker(coverageIndex, instance.demandTowns());
        int cellCount = grid.members.length;
        int[] anchorByCell = new int[cellCount];
        long[] gainByCell = new long[cellCount];
        int[] versionByCell = new int[cellCount];
        boolean[] done = new boolean[cellCount];
        PriorityQueue<CellEntry> heap = new PriorityQueue<>(
                Comparator.comparingLong(CellEntry::gain).reversed()
                        .thenComparingInt(CellEntry::cell));

        for (int cell = 0; cell < cellCount; cell++) {
            recomputeCell(cell, grid, selectionCoverage, anchorByCell, gainByCell, statistics);
            if (gainByCell[cell] > 0) {
                heap.add(new CellEntry(cell, versionByCell[cell], gainByCell[cell]));
            }
        }

        List<Integer> anchors = new ArrayList<>();
        while (!heap.isEmpty()) {
            CellEntry entry = heap.remove();
            statistics.heapPops++;
            int cell = entry.cell();
            if (done[cell] || entry.version() != versionByCell[cell]) {
                statistics.staleEntries++;
                continue;
            }
            // Projection distortion could in principle let a distant cell share a town,
            // so confirm the gain before trusting a non-neighbour-refreshed entry.
            if (selectionCoverage.marginalPopulationGain(anchorByCell[cell]) != entry.gain()) {
                statistics.staleEntries++;
                recomputeCell(cell, grid, selectionCoverage, anchorByCell, gainByCell, statistics);
                versionByCell[cell]++;
                if (gainByCell[cell] > 0) {
                    heap.add(new CellEntry(cell, versionByCell[cell], gainByCell[cell]));
                }
                continue;
            }

            selectionCoverage.applyStop(anchorByCell[cell]);
            anchors.add(anchorByCell[cell]);
            done[cell] = true;

            for (int neighbour : grid.neighboursOf(cell)) {
                if (done[neighbour]) {
                    continue;
                }
                recomputeCell(neighbour, grid, selectionCoverage, anchorByCell, gainByCell, statistics);
                versionByCell[neighbour]++;
                if (gainByCell[neighbour] > 0) {
                    heap.add(new CellEntry(neighbour, versionByCell[neighbour], gainByCell[neighbour]));
                }
            }
        }
        return anchors;
    }

    private static void recomputeCell(
            int cell,
            Grid grid,
            CoverageTracker coverage,
            int[] anchorByCell,
            long[] gainByCell,
            MutableStatistics statistics) {
        statistics.cellRecomputations++;
        int bestId = -1;
        long bestGain = 0;
        for (int candidateId : grid.members[cell]) {
            long gain = coverage.marginalPopulationGain(candidateId);
            if (gain > bestGain) {
                bestId = candidateId;
                bestGain = gain;
            }
        }
        anchorByCell[cell] = bestId;
        gainByCell[cell] = bestGain;
    }

    private static Grid buildGrid(ProblemInstance instance, double cellSideKm) {
        CoordinateProjector projector = new CoordinateProjector(instance.depot());
        Map<Long, Integer> cellByKey = new HashMap<>();
        List<List<Integer>> membersByCell = new ArrayList<>();
        List<long[]> cellCoordinates = new ArrayList<>();

        for (CandidateStop stop : instance.candidateStops()) {
            PlanarPoint point = projector.project(stop.coordinate());
            long column = (long) Math.floor(point.xKm() / cellSideKm);
            long row = (long) Math.floor(point.yKm() / cellSideKm);
            Integer cell = cellByKey.get(key(column, row));
            if (cell == null) {
                cell = membersByCell.size();
                cellByKey.put(key(column, row), cell);
                membersByCell.add(new ArrayList<>());
                cellCoordinates.add(new long[] {column, row});
            }
            membersByCell.get(cell).add(stop.id());
        }

        int[][] members = new int[membersByCell.size()][];
        int[][] neighbours = new int[membersByCell.size()][];
        for (int cell = 0; cell < members.length; cell++) {
            members[cell] = membersByCell.get(cell).stream().mapToInt(Integer::intValue).toArray();
            long column = cellCoordinates.get(cell)[0];
            long row = cellCoordinates.get(cell)[1];
            List<Integer> found = new ArrayList<>(8);
            for (long dc = -1; dc <= 1; dc++) {
                for (long dr = -1; dr <= 1; dr++) {
                    Integer neighbour = (dc == 0 && dr == 0) ? null : cellByKey.get(key(column + dc, row + dr));
                    if (neighbour != null) {
                        found.add(neighbour);
                    }
                }
            }
            neighbours[cell] = found.stream().mapToInt(Integer::intValue).toArray();
        }
        return new Grid(members, neighbours);
    }

    private static long key(long column, long row) {
        return (column << 32) ^ (row & 0xffffffffL);
    }

    private static Route insertStop(
            Route route,
            int stopId,
            Insertion insertion,
            ProblemInstance instance,
            CoverageTracker routedCoverage,
            boolean[] inRoute) {
        List<Integer> updated = RouteCalculator.insertStop(route.stopIds(), stopId, insertion.position());
        routedCoverage.applyStop(stopId);
        inRoute[stopId] = true;
        return RouteCalculator.createRoute(updated, instance.depot(), instance.candidateStops());
    }

    private static int countUnrouted(List<Integer> skipped, boolean[] inRoute) {
        int count = 0;
        for (int stopId : skipped) {
            if (!inRoute[stopId]) {
                count++;
            }
        }
        return count;
    }

    private static void validateCoverageIndex(ProblemInstance instance, CoverageIndex coverageIndex) {
        if (coverageIndex.candidateStopCount() != instance.candidateStops().size()) {
            throw new IllegalArgumentException("Coverage index and candidate-stop list have different sizes.");
        }
        if (coverageIndex.demandTownCount() != instance.demandTowns().size()) {
            throw new IllegalArgumentException("Coverage index and demand-town list have different sizes.");
        }
    }

    private record Grid(int[][] members, int[][] neighbours) {
        int[] neighboursOf(int cell) {
            return neighbours[cell];
        }
    }

    private record CellEntry(int cell, int version, long gain) {
    }

    private static final class MutableStatistics {
        private long cellRecomputations;
        private long heapPops;
        private long staleEntries;
    }
}
