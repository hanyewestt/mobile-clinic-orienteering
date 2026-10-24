package coverage;

import geometry.Haversine;
import java.util.List;
import java.util.Objects;
import model.CandidateStop;
import model.DemandTown;

/** Builds the coverage lists shared by both routing approaches. */
public final class CoverageBuilder {
    private CoverageBuilder() {
    }

    /**
     * Computes every candidate-stop/demand-town pair within {@code serviceRadiusKm}.
     * Candidate-stop and demand-town ids must be consecutive and match their list positions.
     */
    public static CoverageIndex build(
            List<CandidateStop> candidateStops,
            List<DemandTown> demandTowns,
            double serviceRadiusKm) {
        Objects.requireNonNull(candidateStops, "candidateStops");
        Objects.requireNonNull(demandTowns, "demandTowns");
        if (serviceRadiusKm < 0.0) {
            throw new IllegalArgumentException("Service radius must not be negative.");
        }

        validateIds(candidateStops, demandTowns);
        int[][] coveredTownIdsByStop = new int[candidateStops.size()][];

        for (CandidateStop candidateStop : candidateStops) {
            IntArrayBuilder coveredTownIds = new IntArrayBuilder();
            for (DemandTown demandTown : demandTowns) {
                double distanceKm = Haversine.distanceKm(candidateStop.coordinate(), demandTown.coordinate());
                if (distanceKm <= serviceRadiusKm) {
                    coveredTownIds.add(demandTown.id());
                }
            }
            coveredTownIdsByStop[candidateStop.id()] = coveredTownIds.toArray();
        }

        return new CoverageIndex(coveredTownIdsByStop, demandTowns.size(), serviceRadiusKm);
    }

    private static void validateIds(List<CandidateStop> candidateStops, List<DemandTown> demandTowns) {
        for (int index = 0; index < candidateStops.size(); index++) {
            if (candidateStops.get(index).id() != index) {
                throw new IllegalArgumentException(
                        "Candidate-stop ids must be consecutive and match their list positions.");
            }
        }
        for (int index = 0; index < demandTowns.size(); index++) {
            if (demandTowns.get(index).id() != index) {
                throw new IllegalArgumentException(
                        "Demand-town ids must be consecutive and match their list positions.");
            }
        }
    }

    private static final class IntArrayBuilder {
        private int[] values = new int[8];
        private int size;

        void add(int value) {
            if (size == values.length) {
                int[] expanded = new int[values.length * 2];
                System.arraycopy(values, 0, expanded, 0, values.length);
                values = expanded;
            }
            values[size++] = value;
        }

        int[] toArray() {
            int[] result = new int[size];
            System.arraycopy(values, 0, result, 0, size);
            return result;
        }
    }
}
