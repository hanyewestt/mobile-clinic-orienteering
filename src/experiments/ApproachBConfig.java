package experiments;

import java.nio.file.Path;
import java.util.Objects;

/** Settings for one reproducible Approach B experiment run. */
public record ApproachBConfig(
        String runName,
        double serviceRadiusKm,
        double travelBudgetKm,
        int maximumTwoOptPasses,
        boolean pruneUnreachable,
        Path outputDirectory) {

    public ApproachBConfig {
        runName = Objects.requireNonNull(runName, "runName");
        outputDirectory = Objects.requireNonNull(outputDirectory, "outputDirectory");
        if (runName.isBlank()) {
            throw new IllegalArgumentException("Run name must not be blank.");
        }
        if (serviceRadiusKm < 0.0 || travelBudgetKm < 0.0 || maximumTwoOptPasses < 0) {
            throw new IllegalArgumentException("Experiment distance and pass values must not be negative.");
        }
    }
}
