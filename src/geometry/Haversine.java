package geometry;

import model.Coordinate;

/** Great-circle distance calculations for latitude/longitude coordinates. */
public final class Haversine {
    private static final double EARTH_RADIUS_KM = 6_371.0088;

    private Haversine() {
    }

    public static double distanceKm(Coordinate first, Coordinate second) {
        double latitudeDifference = Math.toRadians(second.latitude() - first.latitude());
        double longitudeDifference = Math.toRadians(second.longitude() - first.longitude());
        double firstLatitudeRadians = Math.toRadians(first.latitude());
        double secondLatitudeRadians = Math.toRadians(second.latitude());

        double haversine = Math.pow(Math.sin(latitudeDifference / 2.0), 2)
                + Math.cos(firstLatitudeRadians) * Math.cos(secondLatitudeRadians)
                * Math.pow(Math.sin(longitudeDifference / 2.0), 2);
        double centralAngle = 2.0 * Math.atan2(Math.sqrt(haversine), Math.sqrt(1.0 - haversine));
        return EARTH_RADIUS_KM * centralAngle;
    }
}
