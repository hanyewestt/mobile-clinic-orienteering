package model;

import java.util.Objects;

/** A town with population that contributes to the coverage objective. */
public record DemandTown(
        int id,
        long geonameId,
        String name,
        Coordinate coordinate,
        String featureCode,
        long population) {

    public DemandTown {
        if (id < 0) {
            throw new IllegalArgumentException("Demand-town id must not be negative.");
        }
        if (geonameId <= 0) {
            throw new IllegalArgumentException("GeoNames id must be positive.");
        }
        if (population <= 0) {
            throw new IllegalArgumentException("Demand-town population must be positive.");
        }
        name = Objects.requireNonNull(name, "name");
        coordinate = Objects.requireNonNull(coordinate, "coordinate");
        featureCode = Objects.requireNonNull(featureCode, "featureCode");
    }
}
