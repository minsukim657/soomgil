package kr.carecabin.demo;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Fetches one nearby ITS construction record, its closest station, and the latest PM2.5 grade. */
final class SoomgilApiClient {
    interface Callback {
        void onSuccess(EnvironmentReading reading);
        void onFailure(String message);
    }

    private static final String ITS_URL = "https://openapi.its.go.kr:9443/eventInfo";
    private static final String AIR_BASE = "https://apis.data.go.kr/B552584";
    private static final double SEARCH_RADIUS_KM = 25.0;
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ITS_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "soomgil-api");
        thread.setDaemon(true);
        return thread;
    });
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    void fetchAround(double latitude, double longitude, Callback callback) {
        executor.execute(() -> {
            try {
                EnvironmentReading reading = load(latitude, longitude);
                mainHandler.post(() -> callback.onSuccess(reading));
            } catch (Exception exception) {
                String message = safeMessage(exception);
                mainHandler.post(() -> callback.onFailure(message));
            }
        });
    }

    void shutdown() {
        executor.shutdownNow();
    }

    private EnvironmentReading load(double latitude, double longitude) throws Exception {
        requireKey(BuildConfig.ITS_API_KEY, "ITS_API_KEY");
        requireKey(BuildConfig.AIRKOREA_API_KEY, "AIRKOREA_API_KEY");

        JSONObject roadwork = findNearestRoadwork(latitude, longitude);
        double workLatitude = number(roadwork.optString("coordY"));
        double workLongitude = number(roadwork.optString("coordX"));
        JSONObject station = findNearestStation(workLatitude, workLongitude);
        String stationName = station.optString("stationName", "").trim();
        if (stationName.isEmpty()) throw new IOException("주변 측정소를 찾지 못했습니다.");

        JSONObject measurement = fetchLatestMeasurement(stationName);
        return toReading(roadwork, station, measurement);
    }

    private JSONObject findNearestRoadwork(double latitude, double longitude) throws Exception {
        double latDelta = SEARCH_RADIUS_KM / 111.0;
        double lonDelta = SEARCH_RADIUS_KM / (111.0 * Math.max(0.2, Math.cos(Math.toRadians(latitude))));
        String url = ITS_URL + "?apiKey=" + encode(BuildConfig.ITS_API_KEY)
                + "&type=all&eventType=cor"
                + "&minX=" + (longitude - lonDelta)
                + "&maxX=" + (longitude + lonDelta)
                + "&minY=" + (latitude - latDelta)
                + "&maxY=" + (latitude + latDelta)
                + "&getType=json";
        JSONArray items = items(getJson(url));
        JSONObject nearest = null;
        double nearestDistance = SEARCH_RADIUS_KM;
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null || !"공사".equals(item.optString("eventType"))) continue;
            double itemLat = number(item.optString("coordY"));
            double itemLon = number(item.optString("coordX"));
            if (Double.isNaN(itemLat) || Double.isNaN(itemLon)) continue;
            double distance = distanceKm(latitude, longitude, itemLat, itemLon);
            if (distance <= nearestDistance) {
                nearest = item;
                nearestDistance = distance;
            }
        }
        if (nearest == null) throw new IOException("반경 25km 안에서 공사 정보를 찾지 못했습니다.");
        return nearest;
    }

    private JSONObject findNearestStation(double latitude, double longitude) throws Exception {
        String url = AIR_BASE + "/MsrstnInfoInqireSvc/getMsrstnList"
                + "?serviceKey=" + encode(BuildConfig.AIRKOREA_API_KEY)
                + "&returnType=json&numOfRows=1000&pageNo=1";
        JSONArray items = items(getJson(url));
        JSONObject nearestUrban = null;
        JSONObject nearestAny = null;
        double urbanDistance = Double.MAX_VALUE;
        double anyDistance = Double.MAX_VALUE;
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) continue;
            double stationLat = number(item.optString("dmX"));
            double stationLon = number(item.optString("dmY"));
            if (Double.isNaN(stationLat) || Double.isNaN(stationLon)) continue;
            double distance = distanceKm(latitude, longitude, stationLat, stationLon);
            if (distance < anyDistance) {
                nearestAny = item;
                anyDistance = distance;
            }
            String network = item.optString("mangName", "");
            if (("도시대기".equals(network) || "도로변대기".equals(network)) && distance < urbanDistance) {
                nearestUrban = item;
                urbanDistance = distance;
            }
        }
        return nearestUrban != null ? nearestUrban : nearestAny;
    }

    private JSONObject fetchLatestMeasurement(String stationName) throws Exception {
        String url = AIR_BASE + "/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty"
                + "?serviceKey=" + encode(BuildConfig.AIRKOREA_API_KEY)
                + "&returnType=json&numOfRows=1&pageNo=1"
                + "&stationName=" + encode(stationName)
                + "&dataTerm=DAILY&ver=1.3";
        JSONArray items = items(getJson(url));
        JSONObject result = items.optJSONObject(0);
        if (result == null) throw new IOException("측정소 관측값이 비어 있습니다.");
        return result;
    }

    private EnvironmentReading toReading(JSONObject work, JSONObject station, JSONObject measurement) {
        String start = displayDate(work.optString("startDate", ""));
        String end = displayDate(work.optString("endDate", ""));
        LocalDateTime now = LocalDateTime.now(KOREA);
        LocalDateTime startDate = parseDate(work.optString("startDate", ""));
        LocalDateTime endDate = parseDate(work.optString("endDate", ""));
        EnvironmentReading.WorkStatus status;
        if (startDate == null || startDate.isAfter(now)) {
            status = EnvironmentReading.WorkStatus.UNKNOWN;
        } else if (endDate != null && endDate.isBefore(now)) {
            status = EnvironmentReading.WorkStatus.ENDED;
        } else {
            status = EnvironmentReading.WorkStatus.IN_PROGRESS;
        }

        String roadName = firstNonBlank(work.optString("roadName", ""), "도로명 미제공");
        String message = work.optString("message", "").trim();
        String segment = parseSegment(message, roadName);
        String direction = firstNonBlank(work.optString("roadDrcType", ""), "방향 정보 없음");
        String validFrom = firstNonBlank(start, "시작 시각 미제공");
        String validUntil = firstNonBlank(end, "종료 시각 미제공");
        String rawGrade = firstNonBlank(measurement.optString("pm25Grade1h", ""),
                measurement.optString("pm25Grade", ""));
        String gradeLabel = gradeLabel(rawGrade);
        double stationDistance = distanceKm(
                number(station.optString("dmX")), number(station.optString("dmY")),
                number(work.optString("coordY")), number(work.optString("coordX")));
        return new EnvironmentReading(status, roadName, segment, direction, validFrom, validUntil,
                station.optString("stationName", "측정소 미제공"), rawGrade, gradeLabel,
                firstNonBlank(measurement.optString("dataTime", ""), "관측 시각 미제공"),
                stationDistance, null, number(work.optString("coordY")), number(work.optString("coordX")));
    }

    private JSONObject getJson(String address) throws IOException, JSONException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestProperty("Accept", "application/json");
            int responseCode = connection.getResponseCode();
            InputStream stream = responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = readFully(stream);
            if (responseCode >= 400) throw new IOException("API HTTP " + responseCode);
            JSONObject json = new JSONObject(body);
            JSONObject serviceError = json.optJSONObject("OpenAPI_ServiceResponse");
            if (serviceError != null) {
                JSONObject header = serviceError.optJSONObject("cmmMsgHeader");
                String reason = header == null ? "인증 또는 이용 권한 오류" : header.optString("returnAuthMsg", "인증 오류");
                throw new IOException(reason);
            }
            JSONObject response = json.optJSONObject("response");
            JSONObject header = response == null ? json.optJSONObject("header") : response.optJSONObject("header");
            if (header != null) {
                String code = header.optString("resultCode", "");
                if (!code.isEmpty() && !"0".equals(code) && !"00".equals(code)) {
                    throw new IOException(header.optString("resultMsg", "API 응답 오류"));
                }
            }
            return json;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private JSONArray items(JSONObject root) throws IOException {
        JSONObject response = root.optJSONObject("response");
        JSONObject body = response == null ? root.optJSONObject("body") : response.optJSONObject("body");
        if (body == null) throw new IOException("API 응답 형식이 올바르지 않습니다.");
        Object rawItems = body.opt("items");
        if (rawItems instanceof JSONArray array) return array;
        if (rawItems instanceof JSONObject object) {
            JSONArray singleton = new JSONArray();
            singleton.put(object);
            return singleton;
        }
        return new JSONArray();
    }

    private String readFully(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private String parseSegment(String message, String roadName) {
        if (!message.isEmpty()) {
            String[] fields = message.split("::", -1);
            if (fields.length >= 4) {
                String from = fields[2].trim();
                String to = fields[3].trim();
                if (!from.isEmpty() && !to.isEmpty()) return from.equals(to) ? from : from + " → " + to;
                if (!from.isEmpty()) return from;
            }
            return message;
        }
        return roadName + " 좌표 주변";
    }

    private String gradeLabel(String grade) {
        return switch (grade) {
            case "1" -> "좋음";
            case "2" -> "보통";
            case "3" -> "나쁨";
            case "4" -> "매우 나쁨";
            default -> "확인 불가";
        };
    }

    private LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.parse(value, ITS_TIME);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String displayDate(String value) {
        LocalDateTime date = parseDate(value);
        return date == null ? "" : DISPLAY_TIME.format(date);
    }

    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371.0;
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private double number(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() || "-".equals(first) ? fallback : first;
    }

    private String encode(String value) throws IOException {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception exception) {
            throw new IOException("요청 값을 인코딩하지 못했습니다.", exception);
        }
    }

    private void requireKey(String value, String propertyName) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException("local.properties에 " + propertyName + "를 설정해 주세요.");
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "API 요청을 완료하지 못했습니다.";
        if (!BuildConfig.ITS_API_KEY.isBlank()) message = message.replace(BuildConfig.ITS_API_KEY, "[키 숨김]");
        if (!BuildConfig.AIRKOREA_API_KEY.isBlank()) message = message.replace(BuildConfig.AIRKOREA_API_KEY, "[키 숨김]");
        return message;
    }
}

