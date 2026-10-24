package algorithms;

/** Demand-town evidence for the population newly gained by one selected stop. */
public record NewlyServedTown(
        int demandTownId,
        long geonameId,
        String name,
        double latitude,
        double longitude,
        long population,
        double distanceFromStopKm) {

    public NewlyServedTown {
        if (demandTownId < 0 || geonameId <= 0 || name == null || population <= 0 || distanceFromStopKm < 0.0) {
            throw new IllegalArgumentException("Newly served town values are invalid.");
        }
    }
}
