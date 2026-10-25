package algorithms;

import coverage.CoverageChange;
import coverage.CoverageIndex;
import coverage.CoverageTracker;
import geometry.CoordinateProjector;
import geometry.Haversine;
import geometry.PlanarPoint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import model.CandidateStop;
import model.Coordinate;
import model.ProblemInstance;
import model.Route;
import model.RunResult;
import routing.CheapestInsertion;
import routing.Insertion;
import routing.RouteCalculator;
import routing.TwoOptPolisher;
import routing.TwoOptResult;
import trace.StepRecorder;

/**
 * Approach B: choose stops by coverage alone using a hash grid, then route them.
 *
 * <p>Sites are hashed into square cells slightly wider than {@code 2r}. Two
 * sites that can serve the same town are at most {@code 2r} apart, so picking
 * a site can only lower the gains of sites in its own cell and the 8 cells
 * around it. Each cell keeps one live heap entry holding its current best
 * site; after a pick, only those 9 cells are refreshed, and older entries are
 * recognised as stale by a version number. The live entries are therefore
 * always exact, and the selection order equals plain greedy maximum coverage.
 *
 * <p>Routing ignores coverage: anchors are inserted at their cheapest position
 * in selection order, skipping any that break the budget. The skipped anchors
 * are retried after the same 2-opt polish that Approach A uses.
 */
public final class GridClusterSolver implements RoutingSolver {
    /**
     * Cells are 1% wider than {@code 2r}. The equirectangular projection
     * stretches east-west distances by up to about 0.6% across Peninsular
     * Malaysia, and the 3x3 update is exact only if two sites that share a
     * town are never more than one cell apart in projected space.
     */
    public static final double CELL_SIDE_MARGIN = 1.01;
    private static final double SCORE_EPSILON = 1.0e-9;
    private static final double DISTANCE_EPSILON = 1.0e-9;

    private final int maximumTwoOptPasses;
    private final boolean pruneUnreachable;
    private final StepRecorder steps;
    private final boolean selfCheck;

    public GridClusterSolver(int maximumTwoOptPasses) {
        this(maximumTwoOptPasses, true, StepRecorder.NONE);
    }

    /**
     * @param pruneUnreachable drop sites whose solo round trip from the depot
     *     already exceeds the budget; no route can ever visit them
     */
    public GridClusterSolver(int maximumTwoOptPasses, boolean pruneUnreachable, StepRecorder steps) {
        this(maximumTwoOptPasses, pruneUnreachable, steps, false);
    }

    /** {@code selfCheck} re-verifies every gain by brute force after each pick; for tests only. */
    GridClusterSolver(int maximumTwoOptPasses, boolean pruneUnreachable, StepRecorder steps, boolean selfCheck) {
        if (maximumTwoOptPasses < 0) {
            throw new IllegalArgumentException("Solver limits must not be negative.");
        }
        this.maximumTwoOptPasses = maximumTwoOptPasses;
        this.pruneUnreachable = pruneUnreachable;
        this.steps = Objects.requireNonNull(steps, "steps");
        this.selfCheck = selfCheck;
    }

    @Override
    public RunResult solve(ProblemInstance instance, CoverageIndex coverageIndex) {
        return solveDetailed(instance, coverageIndex).runResult();
    }

    public GridClusterResult solveDetailed(ProblemInstance instance, CoverageIndex coverageIndex) {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(coverageIndex, "coverageIndex");
        if (coverageIndex.candidateStopCount() != instance.candidateStops().size()
                || coverageIndex.demandTownCount() != instance.demandTowns().size()) {
            throw new IllegalArgumentException("Coverage index does not match the problem instance.");
        }

        long startedAtNanos = System.nanoTime();
        Statistics statistics = new Statistics();

        steps.phase("grid", "Hash every useful candidate site into square cells of side about 2r. Only the "
                + "8 neighbouring cells can share towns with a cell, so updates stay local.");
        Grid grid = buildGrid(instance, coverageIndex, statistics);

        steps.phase("select", "Coverage-only selection: pop the cell whose best site serves the most "
                + "unplanned population, pick that site as an anchor, then refresh only the 3x3 block "
                + "of cells around it. Route cost is ignored here.");
        CoverageTracker planned = new CoverageTracker(coverageIndex, instance.demandTowns());
        List<Integer> anchors = selectAnchors(instance, coverageIndex, grid, planned, statistics);

        steps.phase("route", "Insert anchors in the order they were picked, each at its cheapest position. "
                + "An anchor that would break the budget is skipped and kept for the refill.");
        CoverageTracker served = new CoverageTracker(coverageIndex, instance.demandTowns());
        Route route = RouteCalculator.createRoute(List.of(), instance.depot(), instance.candidateStops());
        List<Integer> skipped = new ArrayList<>();
        for (int order = 0; order < anchors.size(); order++) {
            int anchor = anchors.get(order);
            long gain = served.marginalPopulationGain(anchor);
            Insertion insertion = CheapestInsertion.findBest(
                    route.stopIds(), anchor, instance.depot(), instance.candidateStops());
            if (!RouteCalculator.fitsBudget(
                    route.totalDistanceKm(), insertion.addedDistanceKm(), instance.travelBudgetKm())) {
                skipped.add(anchor);
                steps.consider(anchor, gain, insertion.addedDistanceKm(), insertion.position(),
                        score(gain, insertion.addedDistanceKm()), StepRecorder.DEFER, -1, Double.NaN,
                        grid.cellOfSite[anchor],
                        String.format(Locale.ROOT,
                                "Anchor %d of %d would make the route %.1f km, over the %.1f km budget. "
                                        + "Skipped and kept for the refill.",
                                order + 1, anchors.size(),
                                route.totalDistanceKm() + insertion.addedDistanceKm(), instance.travelBudgetKm()));
                continue;
            }
            steps.consider(anchor, gain, insertion.addedDistanceKm(), insertion.position(),
                    score(gain, insertion.addedDistanceKm()), StepRecorder.ACCEPT, -1, Double.NaN,
                    grid.cellOfSite[anchor],
                    String.format(Locale.ROOT,
                            "Anchor %d of %d fits the remaining budget, so it goes in at its cheapest position.",
                            order + 1, anchors.size()));
            route = acceptStop(route, anchor, insertion, instance, coverageIndex, served);
        }

        steps.phase("polish", "2-opt: reverse route segments when that shortens the tour (at most "
                + maximumTwoOptPasses + " passes, one move per pass). Same code as Approach A.");
        TwoOptResult polished = TwoOptPolisher.polish(
                route, instance.depot(), instance.candidateStops(), maximumTwoOptPasses,
                steps.enabled() ? (stopIds, first, second, changeKm, lengthKm) -> steps.route(
                        stopIds, lengthKm, String.format(Locale.ROOT,
                                "2-opt reversed route positions %d to %d, saving %.3f km.",
                                first + 1, second + 1, -changeKm)) : null);
        route = polished.route();
        if (polished.improvements() == 0) {
            steps.note("2-opt found no move that shortens the route.");
        }

        steps.phase("refill", "Refill: retry the skipped anchors with whatever budget is left, "
                + "best population per extra km first (same rule as Approach A).");
        int refilledStops = 0;
        while (true) {
            int bestAnchor = -1;
            Insertion bestInsertion = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int anchor : skipped) {
                if (route.stopIds().contains(anchor)) {
                    continue;
                }
                long gain = served.marginalPopulationGain(anchor);
                if (gain == 0) {
                    continue;
                }
                Insertion insertion = CheapestInsertion.findBest(
                        route.stopIds(), anchor, instance.depot(), instance.candidateStops());
                if (!RouteCalculator.fitsBudget(
                        route.totalDistanceKm(), insertion.addedDistanceKm(), instance.travelBudgetKm())) {
                    continue;
                }
                double candidateScore = score(gain, insertion.addedDistanceKm());
                if (candidateScore > bestScore) {
                    bestScore = candidateScore;
                    bestAnchor = anchor;
                    bestInsertion = insertion;
                }
            }
            if (bestAnchor < 0) {
                steps.note(String.format(Locale.ROOT,
                        "None of the %d skipped anchors both adds coverage and fits the %.2f km left. Done.",
                        skipped.size(), instance.travelBudgetKm() - route.totalDistanceKm()));
                break;
            }
            steps.consider(bestAnchor, served.marginalPopulationGain(bestAnchor),
                    bestInsertion.addedDistanceKm(), bestInsertion.position(), bestScore,
                    StepRecorder.ACCEPT, -1, Double.NaN, grid.cellOfSite[bestAnchor],
                    "Best-scoring skipped anchor that now fits the remaining budget.");
            route = acceptStop(route, bestAnchor, bestInsertion, instance, coverageIndex, served);
            refilledStops++;
        }

        long runtimeMillis = (System.nanoTime() - startedAtNanos) / 1_000_000;
        RunResult runResult = new RunResult(
                "Approach B - grid clusters, then route",
                route,
                served.coveredPopulation(),
                served.servedTownCount(),
                runtimeMillis);
        GridClusterResult result = new GridClusterResult(
                runResult,
                grid.cellSideKm,
                grid.cells.size(),
                statistics.prunedSites,
                anchors.size(),
                statistics.heapPops,
                statistics.stalePops,
                statistics.cellRefreshes,
                statistics.siteEvaluations,
                skipped.size(),
                refilledStops,
                polished.improvements());

        if (steps.enabled()) {
            Map<String, Number> counters = new LinkedHashMap<>();
            counters.put("Grid cells (non-empty)", result.nonEmptyCells());
            counters.put("Cell side (km)", result.cellSideKm());
            counters.put("Unreachable sites pruned", result.prunedSites());
            counters.put("Anchors selected", result.anchorsSelected());
            counters.put("Heap pops", result.heapPops());
            counters.put("Stale pops", result.stalePops());
            counters.put("Cell refreshes", result.cellRefreshes());
            counters.put("Site evaluations", result.siteEvaluations());
            counters.put("Anchors skipped", result.anchorsSkipped());
            counters.put("Refilled stops", result.refilledStops());
            counters.put("2-opt improvements", result.twoOptImprovements());
            steps.result(served.coveredPopulation(), served.servedTownCount(),
                    route.totalDistanceKm(), route.stopIds().size(), runtimeMillis, counters);
        }
        return result;
    }

    private Grid buildGrid(ProblemInstance instance, CoverageIndex coverageIndex, Statistics statistics) {
        double cellSideKm = Math.max(2.0 * instance.serviceRadiusKm() * CELL_SIDE_MARGIN, 1.0e-3);
        CoordinateProjector projector = new CoordinateProjector(instance.depot());
        Grid grid = new Grid(cellSideKm, instance.candidateStops().size());

        int noCoverage = 0;
        for (CandidateStop site : instance.candidateStops()) {
            if (coverageIndex.coveredTownCount(site.id()) == 0) {
                noCoverage++;
                continue;
            }
            if (pruneUnreachable && 2.0 * Haversine.distanceKm(instance.depot(), site.coordinate())
                    > instance.travelBudgetKm() + DISTANCE_EPSILON) {
                statistics.prunedSites++;
                continue;
            }
            PlanarPoint point = projector.project(site.coordinate());
            int column = (int) Math.floor(point.xKm() / cellSideKm);
            int row = (int) Math.floor(point.yKm() / cellSideKm);
            long key = Grid.key(column, row);
            Cell cell = grid.cellsByKey.get(key);
            if (cell == null) {
                cell = new Cell(grid.cells.size(), column, row);
                grid.cellsByKey.put(key, cell);
                grid.cells.add(cell);
            }
            cell.siteIds.add(site.id());
            grid.cellOfSite[site.id()] = cell.id;
        }

        if (steps.enabled()) {
            List<double[]> bounds = new ArrayList<>(grid.cells.size());
            for (Cell cell : grid.cells) {
                Coordinate low = projector.unproject(new PlanarPoint(cell.column * cellSideKm, cell.row * cellSideKm));
                Coordinate high = projector.unproject(
                        new PlanarPoint((cell.column + 1) * cellSideKm, (cell.row + 1) * cellSideKm));
                bounds.add(new double[] {low.latitude(), low.longitude(), high.latitude(), high.longitude()});
            }
            steps.grid(cellSideKm, bounds, String.format(Locale.ROOT,
                    "%d non-empty cells of side %.2f km (2r plus a 1%% projection margin). "
                            + "%d sites serve no town within r and %d sites are too far for even a solo "
                            + "round trip, so neither is placed in the grid.",
                    grid.cells.size(), cellSideKm, noCoverage, statistics.prunedSites));
        }
        return grid;
    }

    private List<Integer> selectAnchors(
            ProblemInstance instance,
            CoverageIndex coverageIndex,
            Grid grid,
            CoverageTracker planned,
            Statistics statistics) {
        boolean[] chosen = new boolean[instance.candidateStops().size()];
        PriorityQueue<CellEntry> heap = new PriorityQueue<>(
                Comparator.comparingLong(CellEntry::gain).reversed().thenComparingInt(CellEntry::cellId));
        for (Cell cell : grid.cells) {
            refresh(cell, planned, chosen, statistics, instance, "Initial best site in this cell.");
            if (cell.bestGain > 0) {
                heap.add(new CellEntry(cell.id, cell.bestSiteId, cell.bestGain, cell.version));
            }
        }

        List<Integer> anchors = new ArrayList<>();
        while (!heap.isEmpty()) {
            CellEntry entry = heap.remove();
            statistics.heapPops++;
            Cell cell = grid.cells.get(entry.cellId());
            if (entry.version() != cell.version) {
                statistics.stalePops++;
                steps.consider(entry.siteId(), entry.gain(), Double.NaN, -1, entry.gain(), StepRecorder.SKIP,
                        -1, Double.NaN, cell.id,
                        "Stale heap entry: this cell was refreshed after a nearby pick, and its newer entry "
                                + "is already in the heap. Dropped.");
                continue;
            }

            int siteId = entry.siteId();
            if (selfCheck) {
                verifyGlobalBest(entry.gain(), grid, planned, chosen);
            }
            if (steps.enabled()) {
                CellEntry next = heap.peek();
                steps.consider(siteId, entry.gain(), Double.NaN, -1, entry.gain(), StepRecorder.ACCEPT,
                        next == null ? -1 : next.siteId(), next == null ? Double.NaN : next.gain(), cell.id,
                        "This cell's entry is current (no nearby pick since its last refresh), so its gain is "
                                + "exact and it is the largest in the heap. Its best site becomes an anchor. "
                                + "Score here is plain population: route cost is not considered.");
            }

            int[] newlyPlanned = steps.enabled() ? unservedTowns(siteId, coverageIndex, planned) : null;
            CoverageChange change = planned.applyStop(siteId);
            chosen[siteId] = true;
            anchors.add(siteId);
            steps.select(siteId, change.newlyCoveredPopulation(), newlyPlanned, cell.id, String.format(Locale.ROOT,
                    "Anchor %d: %s plans %d new towns. Only the 3x3 block of cells around it can lose value, "
                            + "so only those are refreshed.",
                    anchors.size(), instance.candidateStops().get(siteId).name(), change.newlyCoveredTowns()));

            String reason = "Refreshed because " + instance.candidateStops().get(siteId).name()
                    + " was picked in this 3x3 block.";
            for (int columnOffset = -1; columnOffset <= 1; columnOffset++) {
                for (int rowOffset = -1; rowOffset <= 1; rowOffset++) {
                    Cell neighbour = grid.cellsByKey.get(Grid.key(cell.column + columnOffset, cell.row + rowOffset));
                    if (neighbour == null) {
                        continue;
                    }
                    refresh(neighbour, planned, chosen, statistics, instance, reason);
                    statistics.cellRefreshes++;
                    if (neighbour.bestGain > 0) {
                        heap.add(new CellEntry(neighbour.id, neighbour.bestSiteId, neighbour.bestGain, neighbour.version));
                    }
                }
            }
            if (selfCheck) {
                verifyCells(grid, planned, chosen);
            }
        }
        steps.note(String.format(Locale.ROOT,
                "Selection ends with %d anchors: every town reachable from a usable site is now planned.",
                anchors.size()));
        return anchors;
    }

    private void refresh(
            Cell cell,
            CoverageTracker planned,
            boolean[] chosen,
            Statistics statistics,
            ProblemInstance instance,
            String reason) {
        int bestSiteId = -1;
        long bestGain = 0;
        for (int siteId : cell.siteIds) {
            if (chosen[siteId]) {
                continue;
            }
            long gain = planned.marginalPopulationGain(siteId);
            statistics.siteEvaluations++;
            if (gain > bestGain) {
                bestGain = gain;
                bestSiteId = siteId;
            }
        }
        cell.version++;
        cell.bestSiteId = bestSiteId;
        cell.bestGain = bestGain;
        if (steps.enabled()) {
            steps.cell(cell.id, bestSiteId, bestGain,
                    bestGain > 0 ? StepRecorder.CELL_OPEN : StepRecorder.CELL_EMPTY,
                    bestGain > 0
                            ? reason + " Best site now " + instance.candidateStops().get(bestSiteId).name() + "."
                            : reason + " No site here adds unplanned population any more.");
        }
    }

    private Route acceptStop(
            Route route,
            int siteId,
            Insertion insertion,
            ProblemInstance instance,
            CoverageIndex coverageIndex,
            CoverageTracker served) {
        int[] newlyServed = steps.enabled() ? unservedTowns(siteId, coverageIndex, served) : null;
        List<Integer> stopIds = RouteCalculator.insertStop(route.stopIds(), siteId, insertion.position());
        CoverageChange change = served.applyStop(siteId);
        Route updated = RouteCalculator.createRoute(stopIds, instance.depot(), instance.candidateStops());
        if (steps.enabled()) {
            steps.insert(siteId, insertion.position(), insertion.addedDistanceKm(), change.newlyCoveredPopulation(),
                    newlyServed, updated.totalDistanceKm(), String.format(Locale.ROOT,
                            "Inserted at route position %d. %d new towns served; route is now %.1f of %.1f km.",
                            insertion.position() + 1, change.newlyCoveredTowns(),
                            updated.totalDistanceKm(), instance.travelBudgetKm()));
        }
        return updated;
    }

    private static int[] unservedTowns(int siteId, CoverageIndex coverageIndex, CoverageTracker tracker) {
        int count = coverageIndex.coveredTownCount(siteId);
        int[] towns = new int[count];
        int size = 0;
        for (int position = 0; position < count; position++) {
            int townId = coverageIndex.coveredTownIdAt(siteId, position);
            if (!tracker.isServed(townId)) {
                towns[size++] = townId;
            }
        }
        return java.util.Arrays.copyOf(towns, size);
    }

    /** Test hook: the popped gain must equal the best gain over every unchosen gridded site. */
    private static void verifyGlobalBest(long poppedGain, Grid grid, CoverageTracker planned, boolean[] chosen) {
        long best = 0;
        for (Cell cell : grid.cells) {
            for (int siteId : cell.siteIds) {
                if (!chosen[siteId]) {
                    best = Math.max(best, planned.marginalPopulationGain(siteId));
                }
            }
        }
        if (best != poppedGain) {
            throw new IllegalStateException("Grid selection is not exact: popped " + poppedGain + ", best " + best);
        }
    }

    /** Test hook: every cell's stored best gain must match a brute-force recomputation. */
    private static void verifyCells(Grid grid, CoverageTracker planned, boolean[] chosen) {
        for (Cell cell : grid.cells) {
            long best = 0;
            for (int siteId : cell.siteIds) {
                if (!chosen[siteId]) {
                    best = Math.max(best, planned.marginalPopulationGain(siteId));
                }
            }
            if (best != cell.bestGain) {
                throw new IllegalStateException("Cell " + cell.id + " has stale gain " + cell.bestGain
                        + " but its true best is " + best + ".");
            }
        }
    }

    private static double score(long gain, double addedDistanceKm) {
        return gain / (addedDistanceKm + SCORE_EPSILON);
    }

    private record CellEntry(int cellId, int siteId, long gain, int version) {
    }

    private static final class Cell {
        private final int id;
        private final int column;
        private final int row;
        private final List<Integer> siteIds = new ArrayList<>();
        private int version;
        private int bestSiteId = -1;
        private long bestGain;

        private Cell(int id, int column, int row) {
            this.id = id;
            this.column = column;
            this.row = row;
        }
    }

    private static final class Grid {
        private final double cellSideKm;
        private final Map<Long, Cell> cellsByKey = new HashMap<>();
        private final List<Cell> cells = new ArrayList<>();
        private final int[] cellOfSite;

        private Grid(double cellSideKm, int siteCount) {
            this.cellSideKm = cellSideKm;
            this.cellOfSite = new int[siteCount];
            java.util.Arrays.fill(cellOfSite, -1);
        }

        private static long key(int column, int row) {
            return ((long) column << 32) ^ (row & 0xffffffffL);
        }
    }

    private static final class Statistics {
        private int prunedSites;
        private long heapPops;
        private long stalePops;
        private long cellRefreshes;
        private long siteEvaluations;
    }
}
