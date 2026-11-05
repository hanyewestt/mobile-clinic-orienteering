package algorithms;

import java.util.Objects;
import model.RunResult;

/** Approach B's run result plus the grid and heap counters used in experiments. */
public record GridClusterResult(
        RunResult runResult,
        double cellSideKm,
        int nonEmptyCells,
        int prunedSites,
        int anchorsSelected,
        long heapPops,
        long stalePops,
        long cellRefreshes,
        long siteEvaluations,
        int anchorsSkipped,
        int refilledStops,
        int twoOptImprovements) {

    public GridClusterResult {
        Objects.requireNonNull(runResult, "runResult");
    }
}
