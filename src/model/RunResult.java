package model;

import java.util.Objects;

/** The outcome of one solver run, ready for reporting and experiment comparison. */
public record RunResult(
        String algorithmName,
        Route route,
        long populationCovered,
        int townsCovered,
        long runtimeMillis) {

    public RunResult {
        algorithmName = Objects.requireNonNull(algorithmName, "algorithmName");
        route = Objects.requireNonNull(route, "route");
        if (populationCovered < 0) {
            throw new IllegalArgumentException("Covered population must not be negative.");
        }
        if (townsCovered < 0) {
            throw new IllegalArgumentException("Covered-town count must not be negative.");
        }
        if (runtimeMillis < 0) {
            throw new IllegalArgumentException("Runtime must not be negative.");
        }
    }
}
