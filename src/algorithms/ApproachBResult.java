package algorithms;

import java.util.Objects;
import model.RunResult;

/** Extra measurements needed to compare Approach B with Approach A. */
public record ApproachBResult(
        RunResult runResult,
        double cellSideKm,
        int nonEmptyCells,
        int anchorsSelected,
        long cellRecomputations,
        long heapPops,
        long staleEntries,
        int skippedAnchors,
        int refilledStops,
        int twoOptImprovements) {

    public ApproachBResult {
        runResult = Objects.requireNonNull(runResult, "runResult");
        if (cellSideKm <= 0.0) {
            throw new IllegalArgumentException("Grid cell side must be positive.");
        }
        if (nonEmptyCells < 0 || anchorsSelected < 0 || cellRecomputations < 0 || heapPops < 0
                || staleEntries < 0 || skippedAnchors < 0 || refilledStops < 0 || twoOptImprovements < 0) {
            throw new IllegalArgumentException("Approach B statistics must not be negative.");
        }
    }
}
