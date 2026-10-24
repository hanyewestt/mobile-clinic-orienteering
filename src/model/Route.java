package model;

import java.util.List;
import java.util.Objects;

/** A closed clinic route represented by candidate-stop ids in visit order. */
public record Route(List<Integer> stopIds, double totalDistanceKm) {
    public Route {
        stopIds = List.copyOf(Objects.requireNonNull(stopIds, "stopIds"));
        if (totalDistanceKm < 0.0) {
            throw new IllegalArgumentException("Route distance must not be negative.");
        }
    }
}
