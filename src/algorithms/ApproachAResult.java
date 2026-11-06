package algorithms;

import java.util.Objects;
import java.util.List;
import model.RunResult;

/** Extra measurements from one Approach A run. */
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
