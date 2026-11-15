package kr.carecabin.demo;

import android.content.Context;
import android.util.Log;
import android.os.Handler;
import android.os.Looper;

import java.lang.reflect.Method;
import java.util.List;

import ai.pleos.playground.vehicle.Vehicle;

/** Pleos Vehicle SDK lifecycle, HVAC read, and explicitly requested recirculation test. */
public final class PleosVehicleManager {
    private static final String TAG = "SoomgilPleos";
    private Vehicle vehicle;
    private Object hvac;
    private volatile Object supportedArea;
    private boolean initialized;
    private final Handler verificationHandler = new Handler(Looper.getMainLooper());
    private static final int VERIFY_MAX_ATTEMPTS = 5;
    private static final long VERIFY_RETRY_DELAY_MS = 400;

    public PleosVehicleManager(Context context) {
        vehicle = new Vehicle(context.getApplicationContext());
    }

    public void initialize() {
        if (initialized || vehicle == null) return;
        try {
            vehicle.initialize();
            initialized = true;
            Log.i(TAG, "Vehicle SDK initialize() invoked.");
            discoverHvac(1);
        } catch (RuntimeException error) {
            Log.e(TAG, "Vehicle SDK initialization call failed.", error);
        }
    }

    private void discoverHvac(int attempt) {
        if (!initialized || supportedArea != null) return;
        inspectHvacApiSurface();
        if (attempt < 5) {
            verificationHandler.postDelayed(() -> discoverHvac(attempt + 1), 2000);
        }
    }

    private void inspectHvacApiSurface() {
        if (vehicle == null) return;
        try {
            hvac = vehicle.getClass().getMethod("getHVAC").invoke(vehicle);
            Class<?> seatAreaClass = Class.forName(
                    "ai.pleos.playground.vehicle.api.model.area.SeatArea");
            Class<?> function1 = Class.forName("kotlin.jvm.functions.Function1");
            Log.i(TAG, "HVAC domain ready: " + hvac.getClass().getName());

            Method getState = hvac.getClass().getMethod(
                    "getHvacRecirculationState", seatAreaClass, function1, function1);
            Object capabilitySuccess = java.lang.reflect.Proxy.newProxyInstance(
                    function1.getClassLoader(), new Class<?>[]{function1},
                    (proxy, method, args) -> {
                        if ("invoke".equals(method.getName())) {
                            Object result = args == null || args.length == 0 ? null : args[0];
                            Log.i(TAG, "HVAC recirculation capabilities: " + result);
                            if (result instanceof List<?> areas && !areas.isEmpty()) {
                                Object firstSupportedArea = areas.get(0);
                                if (seatAreaClass.isInstance(firstSupportedArea)) {
                                    supportedArea = firstSupportedArea;
                                    queryRecirculationState(getState, function1, supportedArea);
                                }
                            } else {
                                Log.w(TAG, "No supported HVAC recirculation area returned.");
                            }
                        }
                        return kotlin.Unit.INSTANCE;
                    });
            Object capabilityFailure = java.lang.reflect.Proxy.newProxyInstance(
                    function1.getClassLoader(), new Class<?>[]{function1},
                    (proxy, method, args) -> {
                        if ("invoke".equals(method.getName()))
                            Log.e(TAG, "HVAC capability query failed: " + args[0]);
                        return kotlin.Unit.INSTANCE;
                    });
            hvac.getClass().getMethod("checkHvacRecirculationCapabilities",
                    function1, function1).invoke(hvac, capabilitySuccess, capabilityFailure);
        } catch (Exception error) {
            Log.e(TAG, "Read-only HVAC query setup failed.", error);
        }
    }

    private void queryRecirculationState(Method getState, Class<?> function1, Object area) {
        try {
            Object success = java.lang.reflect.Proxy.newProxyInstance(
                    function1.getClassLoader(), new Class<?>[]{function1},
                    (proxy, method, args) -> {
                        if ("invoke".equals(method.getName()))
                            Log.i(TAG, "HVAC recirculation state [supported area "
                                    + area + "]: " + args[0]);
                        return kotlin.Unit.INSTANCE;
                    });
            Object failure = java.lang.reflect.Proxy.newProxyInstance(
                    function1.getClassLoader(), new Class<?>[]{function1},
                    (proxy, method, args) -> {
                        if ("invoke".equals(method.getName()))
                            Log.e(TAG, "HVAC state query failed [supported area "
                                    + area + "]: " + args[0]);
                        return kotlin.Unit.INSTANCE;
                    });
            Log.i(TAG, "Querying HVAC state for capability-supported area: " + area);
            getState.invoke(hvac, area, success, failure);
        } catch (Exception error) {
            Log.e(TAG, "Could not query HVAC state.", error);
        }
    }

    public interface ControlCallback {
        void onComplete(boolean requestedState, boolean verified, Boolean actualState, String detail);
    }

    public interface StateCallback {
        void onResult(boolean success, Boolean recirculating, String detail);
    }

    /** Reads the actual vehicle mode so a trip can preserve the driver's starting setting. */
    public void getRecirculationState(StateCallback callback) {
        if (hvac == null || supportedArea == null) {
            if (callback != null) callback.onResult(false, null,
                    "지원되는 공조 영역이 아직 준비되지 않았습니다.");
            return;
        }
        try {
            Class<?> function1 = Class.forName("kotlin.jvm.functions.Function1");
            Class<?> seatAreaClass = Class.forName(
                    "ai.pleos.playground.vehicle.api.model.area.SeatArea");
            Object success = java.lang.reflect.Proxy.newProxyInstance(
                    function1.getClassLoader(), new Class<?>[]{function1},
                    (proxy, method, args) -> {
                        if ("invoke".equals(method.getName())) {
                            Object value = args == null || args.length == 0 ? null : args[0];
                            Boolean actual = value instanceof Boolean ? (Boolean) value : null;
                            Log.i(TAG, "Initial HVAC state read: " + actual);
                            if (callback != null) callback.onResult(actual != null, actual,
                                    actual == null ? "차량 공조 상태를 해석할 수 없습니다." : "차량 공조 상태 확인 완료");
                        }
                        return kotlin.Unit.INSTANCE;
                    });
            Object failure = java.lang.reflect.Proxy.newProxyInstance(
                    function1.getClassLoader(), new Class<?>[]{function1},
                    (proxy, method, args) -> {
                        if ("invoke".equals(method.getName())) {
                            String detail = String.valueOf(args[0]);
                            Log.e(TAG, "Initial HVAC state read failed: " + detail);
                            if (callback != null) callback.onResult(false, null, detail);
                        }
                        return kotlin.Unit.INSTANCE;
                    });
            Log.i(TAG, "Reading actual HVAC state before starting trip.");
            hvac.getClass().getMethod("getHvacRecirculationState",
                    seatAreaClass, function1, function1).invoke(hvac, supportedArea, success, failure);
        } catch (Exception error) {
            Log.e(TAG, "Initial HVAC state query setup failed.", error);
            if (callback != null) callback.onResult(false, null, error.toString());
        }
    }

    /** Sends a recirculation request and verifies it by reading the state again. */
    public void setRecirculation(boolean enabled, ControlCallback callback) {
        if (hvac == null || supportedArea == null) {
            if (callback != null) callback.onComplete(enabled, false, null,
                    "지원되는 공조 영역이 아직 준비되지 않았습니다.");
            Log.e(TAG, "HVAC control unavailable: no supported area yet.");
            return;
        }
        try {
            Class<?> function2 = Class.forName("kotlin.jvm.functions.Function2");
            Class<?> function1 = Class.forName("kotlin.jvm.functions.Function1");
            Class<?> seatAreaClass = Class.forName(
                    "ai.pleos.playground.vehicle.api.model.area.SeatArea");
            Object success = java.lang.reflect.Proxy.newProxyInstance(
                    function2.getClassLoader(), new Class<?>[]{function2},
                    (proxy, method, args) -> {
                        if ("invoke".equals(method.getName())) {
                            Log.i(TAG, "HVAC SET acknowledged; verifying requested state=" + enabled);
                            verifyRecirculationState(enabled, callback);
                        }
                        return kotlin.Unit.INSTANCE;
                    });
            Object failure = java.lang.reflect.Proxy.newProxyInstance(
                    function1.getClassLoader(), new Class<?>[]{function1},
                    (proxy, method, args) -> {
                        if ("invoke".equals(method.getName())) {
                            String detail = String.valueOf(args[0]);
                            Log.e(TAG, "HVAC SET failed: " + detail);
                            if (callback != null) callback.onComplete(enabled, false, null, detail);
                        }
                        return kotlin.Unit.INSTANCE;
                    });
            Log.i(TAG, "Requesting HVAC recirculation=" + enabled);
            hvac.getClass().getMethod("setHvacRecirculation",
                    seatAreaClass, boolean.class, function2, function1)
                    .invoke(hvac, supportedArea, enabled, success, failure);
        } catch (Exception error) {
            Log.e(TAG, "HVAC SET invocation failed.", error);
            if (callback != null) callback.onComplete(enabled, false, null, error.toString());
        }
    }

    private void verifyRecirculationState(boolean requested, ControlCallback callback) {
        verifyRecirculationState(requested, callback, 1);
    }

    /** Vehicle state propagation can lag behind the SET acknowledgement; re-read before failing. */
    private void verifyRecirculationState(boolean requested, ControlCallback callback, int attempt) {
        try {
            Class<?> function1 = Class.forName("kotlin.jvm.functions.Function1");
            Class<?> seatAreaClass = Class.forName(
                    "ai.pleos.playground.vehicle.api.model.area.SeatArea");
            Object success = java.lang.reflect.Proxy.newProxyInstance(
                    function1.getClassLoader(), new Class<?>[]{function1},
                    (proxy, method, args) -> {
                        if ("invoke".equals(method.getName())) {
                            Object value = args == null || args.length == 0 ? null : args[0];
                            Boolean actual = value instanceof Boolean ? (Boolean) value : null;
                            boolean verified = actual != null && actual == requested;
                            Log.i(TAG, "HVAC verification attempt=" + attempt + ", state=" + actual
                                    + ", requested=" + requested + ", match=" + verified);
                            if (verified || attempt >= VERIFY_MAX_ATTEMPTS || actual == null) {
                                if (callback != null) callback.onComplete(requested, verified, actual,
                                        verified ? "차량 상태 확인 완료" : "재조회 후에도 요청 상태와 실제 상태가 일치하지 않습니다.");
                            } else {
                                verificationHandler.postDelayed(
                                        () -> verifyRecirculationState(requested, callback, attempt + 1),
                                        VERIFY_RETRY_DELAY_MS);
                            }
                        }
                        return kotlin.Unit.INSTANCE;
                    });
            Object failure = java.lang.reflect.Proxy.newProxyInstance(
                    function1.getClassLoader(), new Class<?>[]{function1},
                    (proxy, method, args) -> {
                        if ("invoke".equals(method.getName())) {
                            String detail = String.valueOf(args[0]);
                            Log.e(TAG, "HVAC verification read failed on attempt " + attempt + ": " + detail);
                            if (attempt >= VERIFY_MAX_ATTEMPTS) {
                                if (callback != null) callback.onComplete(requested, false, null, detail);
                            } else {
                                verificationHandler.postDelayed(
                                        () -> verifyRecirculationState(requested, callback, attempt + 1),
                                        VERIFY_RETRY_DELAY_MS);
                            }
                        }
                        return kotlin.Unit.INSTANCE;
                    });
            Method getState = hvac.getClass().getMethod("getHvacRecirculationState",
                    seatAreaClass, function1, function1);
            getState.invoke(hvac, supportedArea, success, failure);
        } catch (Exception error) {
            Log.e(TAG, "HVAC verification setup failed on attempt " + attempt, error);
            if (attempt >= VERIFY_MAX_ATTEMPTS && callback != null)
                callback.onComplete(requested, false, null, error.toString());
            else verificationHandler.postDelayed(
                    () -> verifyRecirculationState(requested, callback, attempt + 1),
                    VERIFY_RETRY_DELAY_MS);
        }
    }

    /** Manual diagnostic buttons retained for isolated testing. */
    public void setRecirculationForTest(boolean enabled) {
        setRecirculation(enabled, (requested, verified, actual, detail) ->
                Log.i(TAG, "Manual HVAC test completed: requested=" + requested
                        + ", verified=" + verified + ", actual=" + actual + ", detail=" + detail));
    }

    public void release() {
        verificationHandler.removeCallbacksAndMessages(null);
        if (vehicle == null) return;
        try {
            vehicle.release();
        } catch (RuntimeException error) {
            Log.w(TAG, "Vehicle SDK release failed.", error);
        } finally {
            vehicle = null;
            hvac = null;
            supportedArea = null;
            initialized = false;
        }
    }
}
