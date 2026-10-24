package experiments;

import algorithms.ScoreRefreshMode;
import java.nio.file.Path;
import java.util.Objects;

/** Settings for one reproducible Approach A experiment run. */
public record ExperimentConfig(
        String runName,
        double serviceRadiusKm,
        double travelBudgetKm,
        ScoreRefreshMode refreshMode,
        int maximumTwoOptPasses,
        Path outputDirectory) {

    public ExperimentConfig {
        runName = Objects.requireNonNull(runName, "runName");
        refreshMode = Objects.requireNonNull(refreshMode, "refreshMode");
        outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
        if (runName.isBlank()) {
            throw new IllegalArgumentException("Run name must not be blank.");
        }
        if (serviceRadiusKm < 0.0 || travelBudgetKm < 0.0 || maximumTwoOptPasses < 0) {
            throw new IllegalArgumentException("Experiment distance and pass values must not be negative.");
        }
    }
}
