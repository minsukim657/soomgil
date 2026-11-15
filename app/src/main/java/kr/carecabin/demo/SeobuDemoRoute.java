package kr.carecabin.demo;

/** GPX demo route along the northbound surface road. Coordinates © OpenStreetMap contributors. */
final class SeobuDemoRoute {
    private static final double[][] POINTS = {
        {37.5152643, 126.881747},
        {37.5157059, 126.8820791},
        {37.5159218, 126.882203},
        {37.5162826, 126.8823542},
        {37.5164911, 126.8824026},
        {37.5169871, 126.8824424},
        {37.5187001, 126.8822234},
        {37.5197888, 126.8820525},
        {37.521357, 126.8818467},
        {37.523342, 126.8816416},
        {37.5239359, 126.8816096},
        {37.5244505, 126.8817115},
        {37.5254096, 126.8822419},
        {37.5257643, 126.8825443},
        {37.5264666, 126.8836949},
        {37.5272283, 126.8859204},
        {37.5279039, 126.887516},
        {37.528523, 126.8883959},
        {37.5292136, 126.889064},
        {37.5300554, 126.8896825},
        {37.5307655, 126.8901697},
        {37.5312346, 126.8905175},
        {37.5320005, 126.8910719}
    };
    private static final int START_POINT = 3;
    private static final int END_POINT = 21;

    record Match(double distanceMeters, double offsetMeters, double headingDegrees) {}

    private final DirectedRoadSegment[] segments = new DirectedRoadSegment[POINTS.length - 1];
    private final double[] distances = new double[POINTS.length];

    SeobuDemoRoute() {
        for (int i = 0; i < segments.length; i++) {
            double[] from = POINTS[i];
            double[] to = POINTS[i + 1];
            segments[i] = new DirectedRoadSegment(from[0], from[1], to[0], to[1]);
            distances[i + 1] = distances[i] + segments[i].lengthMeters;
        }
    }

    double startMeters() { return distances[START_POINT]; }
    double endMeters() { return distances[END_POINT]; }

    Match match(double latitude, double longitude) {
        Match nearest = null;
        for (int i = 0; i < segments.length; i++) {
            DirectedRoadSegment segment = segments[i];
            DirectedRoadSegment.Projection projected = segment.project(latitude, longitude);
            double along = Math.max(0, Math.min(segment.lengthMeters, projected.distanceMeters()));
            double offset = Math.hypot(projected.offsetMeters(), projected.distanceMeters() - along);
            if (nearest == null || offset < nearest.offsetMeters()) {
                nearest = new Match(distances[i] + along, offset, segment.headingDegrees);
            }
        }
        return nearest;
    }
}
