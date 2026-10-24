package model;

import java.util.List;
import java.util.Objects;

/** Immutable input for a solver run. */
public record ProblemInstance(
        String name,
        Coordinate depot,
        List<CandidateStop> candidateStops,
        List<DemandTown> demandTowns,
        double serviceRadiusKm,
        double travelBudgetKm) {

    public ProblemInstance {
        name = Objects.requireNonNull(name, "name");
        depot = Objects.requireNonNull(depot, "depot");
        candidateStops = List.copyOf(Objects.requireNonNull(candidateStops, "candidateStops"));
        demandTowns = List.copyOf(Objects.requireNonNull(demandTowns, "demandTowns"));
        if (serviceRadiusKm < 0.0) {
            throw new IllegalArgumentException("Service radius must not be negative.");
        }
        if (travelBudgetKm < 0.0) {
            throw new IllegalArgumentException("Travel budget must not be negative.");
        }
    }
}
