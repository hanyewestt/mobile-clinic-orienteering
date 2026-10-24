package experiments;

import algorithms.ApproachAResult;
import java.util.Objects;
import model.ProblemInstance;

/** Complete in-memory result for an experiment and its exported artifacts. */
public record ExperimentOutcome(
        ExperimentConfig config,
        ProblemInstance instance,
        ApproachAResult approachAResult) {

    public ExperimentOutcome {
        config = Objects.requireNonNull(config, "config");
        instance = Objects.requireNonNull(instance, "instance");
        approachAResult = Objects.requireNonNull(approachAResult, "approachAResult");
    }
}
