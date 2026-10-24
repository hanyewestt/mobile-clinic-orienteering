package coverage;

import java.util.List;
import java.util.Objects;
import model.DemandTown;

/**
 * Mutable coverage state for one solver run. It ensures a demand town's
 * population is counted at most once even when multiple stops cover it.
 */
public final class CoverageTracker {
    private final CoverageIndex coverageIndex;
    private final long[] populationsByTownId;
    private final boolean[] served;
    private int servedTownCount;
    private long coveredPopulation;

    public CoverageTracker(CoverageIndex coverageIndex, List<DemandTown> demandTowns) {
        this.coverageIndex = Objects.requireNonNull(coverageIndex, "coverageIndex");
        Objects.requireNonNull(demandTowns, "demandTowns");
        if (coverageIndex.demandTownCount() != demandTowns.size()) {
            throw new IllegalArgumentException("Coverage index and demand-town list have different sizes.");
        }

        this.populationsByTownId = new long[demandTowns.size()];
        for (int index = 0; index < demandTowns.size(); index++) {
            DemandTown demandTown = demandTowns.get(index);
            if (demandTown.id() != index) {
                throw new IllegalArgumentException(
                        "Demand-town ids must be consecutive and match their list positions.");
            }
            populationsByTownId[index] = demandTown.population();
        }
        this.served = new boolean[demandTowns.size()];
    }

    public long marginalPopulationGain(int candidateStopId) {
        long populationGain = 0;
        int coveredTownCount = coverageIndex.coveredTownCount(candidateStopId);
        for (int position = 0; position < coveredTownCount; position++) {
            int townId = coverageIndex.coveredTownIdAt(candidateStopId, position);
            if (!served[townId]) {
                populationGain += populationsByTownId[townId];
            }
        }
        return populationGain;
    }

    public CoverageChange applyStop(int candidateStopId) {
        int newlyCoveredTowns = 0;
        long newlyCoveredPopulation = 0;
        int coveredTownCount = coverageIndex.coveredTownCount(candidateStopId);

        for (int position = 0; position < coveredTownCount; position++) {
            int townId = coverageIndex.coveredTownIdAt(candidateStopId, position);
            if (!served[townId]) {
                served[townId] = true;
                newlyCoveredTowns++;
                newlyCoveredPopulation += populationsByTownId[townId];
            }
        }

        servedTownCount += newlyCoveredTowns;
        coveredPopulation += newlyCoveredPopulation;
        return new CoverageChange(newlyCoveredTowns, newlyCoveredPopulation);
    }

    public boolean isServed(int demandTownId) {
        validateDemandTownId(demandTownId);
        return served[demandTownId];
    }

    public int servedTownCount() {
        return servedTownCount;
    }

    public long coveredPopulation() {
        return coveredPopulation;
    }

    private void validateDemandTownId(int demandTownId) {
        if (demandTownId < 0 || demandTownId >= served.length) {
            throw new IndexOutOfBoundsException("Demand-town id out of range: " + demandTownId);
        }
    }
}
