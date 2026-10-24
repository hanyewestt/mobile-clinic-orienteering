package geometry;

import java.util.Objects;
import model.Coordinate;

/**
 * Equirectangular projection centred on an origin. It is used for grid-cell
 * calculations, while Haversine remains the authoritative distance measure.
 */
public final class CoordinateProjector {
    private static final double EARTH_RADIUS_KM = 6_371.0088;
    private final Coordinate origin;
    private final double originLatitudeRadians;

    public CoordinateProjector(Coordinate origin) {
        this.origin = Objects.requireNonNull(origin, "origin");
        this.originLatitudeRadians = Math.toRadians(origin.latitude());
    }

    public PlanarPoint project(Coordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        double longitudeDifference = Math.toRadians(coordinate.longitude() - origin.longitude());
        double latitudeDifference = Math.toRadians(coordinate.latitude() - origin.latitude());

        double xKm = EARTH_RADIUS_KM * longitudeDifference * Math.cos(originLatitudeRadians);
        double yKm = EARTH_RADIUS_KM * latitudeDifference;
        return new PlanarPoint(xKm, yKm);
    }
}
