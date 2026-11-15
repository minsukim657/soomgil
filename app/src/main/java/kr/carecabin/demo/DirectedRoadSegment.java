package kr.carecabin.demo;

/** Local projection for a short, straight, directed test segment. */
final class DirectedRoadSegment {
    private static final double METERS_PER_DEGREE = 111_195;

    record Projection(double distanceMeters, double offsetMeters) {}

    private final double startLatitude;
    private final double startLongitude;
    private final double longitudeScale;
    private final double eastUnit;
    private final double northUnit;
    final double lengthMeters;
    final double headingDegrees;

    DirectedRoadSegment(double startLatitude, double startLongitude,
                        double endLatitude, double endLongitude) {
        this.startLatitude = startLatitude;
        this.startLongitude = startLongitude;
        longitudeScale = METERS_PER_DEGREE * Math.cos(Math.toRadians(startLatitude));
        double east = (endLongitude - startLongitude) * longitudeScale;
        double north = (endLatitude - startLatitude) * METERS_PER_DEGREE;
        lengthMeters = Math.hypot(east, north);
        if (!Double.isFinite(lengthMeters) || lengthMeters < 1) {
            throw new IllegalArgumentException("Expected distinct segment endpoints");
        }
        eastUnit = east / lengthMeters;
        northUnit = north / lengthMeters;
        headingDegrees = Math.toDegrees(Math.atan2(east, north));
    }

    Projection project(double latitude, double longitude) {
        double east = (longitude - startLongitude) * longitudeScale;
        double north = (latitude - startLatitude) * METERS_PER_DEGREE;
        return new Projection(east * eastUnit + north * northUnit,
                Math.abs(east * northUnit - north * eastUnit));
    }
}
