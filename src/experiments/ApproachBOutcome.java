package experiments;

import algorithms.GridClusterResult;
import java.util.Objects;
import model.ProblemInstance;

/** Complete in-memory result for an Approach B experiment and its exported artifacts. */
public record ApproachBOutcome(
        ApproachBConfig config,
        ProblemInstance instance,
        GridClusterResult approachBResult) {

    public ApproachBOutcome {
        config = Objects.requireNonNull(config, "config");
        instance = Objects.requireNonNull(instance, "instance");
        approachBResult = Objects.requireNonNull(approachBResult, "approachBResult");
    }
}
