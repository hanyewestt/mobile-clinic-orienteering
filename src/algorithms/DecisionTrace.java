package algorithms;

import java.util.List;
import java.util.Objects;

/** Read-only evidence for one accepted stop, captured before its coverage is applied. */
public record DecisionTrace(
        int selectionStep,
        int chosenCandidateStopId,
        long marginalPopulation,
        int marginalTownCount,
        int insertionPosition,
        double addedDistanceKm,
        double score,
        double routeLengthBeforeKm,
        double routeLengthAfterKm,
        long populationBefore,
        long populationAfter,
        int townsBefore,
        int townsAfter,
        List<NewlyServedTown> newlyServedTowns) {

    public DecisionTrace {
        if (selectionStep < 1 || chosenCandidateStopId < 0 || marginalPopulation < 0
                || marginalTownCount < 0 || insertionPosition < 0 || addedDistanceKm < 0.0
                || score < 0.0 || routeLengthBeforeKm < 0.0 || routeLengthAfterKm < 0.0
                || populationBefore < 0 || populationAfter < populationBefore || townsBefore < 0
                || townsAfter < townsBefore) {
            throw new IllegalArgumentException("Decision trace values are invalid.");
        }
        newlyServedTowns = List.copyOf(Objects.requireNonNull(newlyServedTowns, "newlyServedTowns"));
    }
}
