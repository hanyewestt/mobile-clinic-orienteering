package coverage;

/**
 * Immutable precomputed coverage lists. A candidate-stop id maps to the ids of
 * demand towns that lie within that stop's configured service radius.
 */
public final class CoverageIndex {
    private final int[][] coveredTownIdsByStop;
    private final int demandTownCount;
    private final double serviceRadiusKm;
    private final long totalCoverageRelations;

    CoverageIndex(int[][] coveredTownIdsByStop, int demandTownCount, double serviceRadiusKm) {
        this.coveredTownIdsByStop = coveredTownIdsByStop;
        this.demandTownCount = demandTownCount;
        this.serviceRadiusKm = serviceRadiusKm;

        long relationCount = 0;
        for (int[] coveredTownIds : coveredTownIdsByStop) {
            relationCount += coveredTownIds.length;
        }
        this.totalCoverageRelations = relationCount;
    }

    public int candidateStopCount() {
        return coveredTownIdsByStop.length;
    }

    public int demandTownCount() {
        return demandTownCount;
    }

    public double serviceRadiusKm() {
        return serviceRadiusKm;
    }

    public long totalCoverageRelations() {
        return totalCoverageRelations;
    }

    public int coveredTownCount(int candidateStopId) {
        validateCandidateStopId(candidateStopId);
        return coveredTownIdsByStop[candidateStopId].length;
    }

    public int coveredTownIdAt(int candidateStopId, int position) {
        validateCandidateStopId(candidateStopId);
        int[] coveredTownIds = coveredTownIdsByStop[candidateStopId];
        if (position < 0 || position >= coveredTownIds.length) {
            throw new IndexOutOfBoundsException("Coverage position out of range: " + position);
        }
        return coveredTownIds[position];
    }

    private void validateCandidateStopId(int candidateStopId) {
        if (candidateStopId < 0 || candidateStopId >= coveredTownIdsByStop.length) {
            throw new IndexOutOfBoundsException("Candidate-stop id out of range: " + candidateStopId);
        }
    }
}
