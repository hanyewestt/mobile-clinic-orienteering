package algorithms;

import java.util.Objects;
import model.RunResult;

/** Measurements from an exact small-instance Held-Karp comparison run. */
public record ExactBaselineResult(
        RunResult runResult,
        long subsetsEvaluated,
        int candidateStopLimit) {

    public ExactBaselineResult {
        runResult = Objects.requireNonNull(runResult, "runResult");
        if (subsetsEvaluated < 0 || candidateStopLimit < 1) {
            throw new IllegalArgumentException("Exact-baseline statistics are invalid.");
        }
    }
}
