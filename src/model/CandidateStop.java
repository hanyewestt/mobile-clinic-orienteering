package model;

import java.util.Objects;

/** A place where the mobile clinic is allowed to stop. */
public record CandidateStop(
        int id,
        long geonameId,
        String name,
        Coordinate coordinate,
        String featureCode) {

    public CandidateStop {
        if (id < 0) {
            throw new IllegalArgumentException("Candidate-stop id must not be negative.");
        }
        if (geonameId <= 0) {
            throw new IllegalArgumentException("GeoNames id must be positive.");
        }
        name = Objects.requireNonNull(name, "name");
        coordinate = Objects.requireNonNull(coordinate, "coordinate");
        featureCode = Objects.requireNonNull(featureCode, "featureCode");
    }
}
