package algorithms;

import java.util.Objects;
import java.util.List;
import model.RunResult;

/** Extra measurements needed to compare lazy and full-recompute Approach A runs. */
public record ApproachAResult(
        RunResult runResult,
        long candidateEvaluations,
        long heapPops,
        int acceptedStops,
        int deferredStops,
        int refilledStops,
        int twoOptImprovements,
        List<DecisionTrace> decisionTrace) {

    public ApproachAResult {
        runResult = Objects.requireNonNull(runResult, "runResult");
        decisionTrace = List.copyOf(Objects.requireNonNull(decisionTrace, "decisionTrace"));
        if (candidateEvaluations < 0 || heapPops < 0 || acceptedStops < 0
                || deferredStops < 0 || refilledStops < 0 || twoOptImprovements < 0) {
            throw new IllegalArgumentException("Approach A statistics must not be negative.");
        }
    }
}
