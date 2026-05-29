package com.basir.ai;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/**
 * v2.3 — runtime permission requests extracted from MainActivity.
 *
 * The Roadmap (see Phase 1 closure plan, item #5) prescribes per-feature
 * just-in-time prompts instead of one upfront mega-prompt that scares blind
 * users with five system dialogs in a row. This controller exposes the
 * primitives needed for that pattern:
 *
 *   - {@link #requestCorePermissions()}  — the (now smaller) baseline ask,
 *     called from Activity.onCreate. Today it requests RECORD_AUDIO,
 *     ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, and POST_NOTIFICATIONS
 *     (API 33+). A later phase will move LOCATION into Emergency mode and
 *     RECORD_AUDIO into the first voice-command launch.
 *   - {@link #hasCamera()} / {@link #requestCamera()} — used by photo
 *     capture screens.
 *   - {@link #hasFineOrCoarseLocation()} — used by Emergency mode.
 *   - {@link #hasMicrophone()} — used by voice command + continuous mode.
 *
 * The host Activity is still responsible for routing
 * {@link Activity#onRequestPermissionsResult(int, String[], int[])} back to
 * the right flow using {@link #REQ_CAMERA_PERM} and
 * {@link #REQ_CORE_PERMISSIONS}.
 */
public final class PermissionController {

    public static final int REQ_CORE_PERMISSIONS = 1001;
    public static final int REQ_CAMERA_PERM      = 1006;

    private final Activity activity;

    public PermissionController(Activity activity) {
        this.activity = activity;
    }

    /** Baseline runtime permissions requested once at app launch. */
    public void requestCorePermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        List<String> need = new ArrayList<>();
        addIfMissing(need, Manifest.permission.RECORD_AUDIO);
        addIfMissing(need, Manifest.permission.ACCESS_FINE_LOCATION);
        addIfMissing(need, Manifest.permission.ACCESS_COARSE_LOCATION);
        if (Build.VERSION.SDK_INT >= 33) {
            addIfMissing(need, "android.permission.POST_NOTIFICATIONS");
        }
        if (!need.isEmpty()) {
            activity.requestPermissions(need.toArray(new String[0]),
                    REQ_CORE_PERMISSIONS);
        }
    }

    public boolean hasCamera() {
        return checkSelf(Manifest.permission.CAMERA);
    }

    public void requestCamera() {
        if (Build.VERSION.SDK_INT < 23) return;
        activity.requestPermissions(new String[]{ Manifest.permission.CAMERA },
                REQ_CAMERA_PERM);
    }

    public boolean hasMicrophone() {
        return checkSelf(Manifest.permission.RECORD_AUDIO);
    }

    public boolean hasFineOrCoarseLocation() {
        return checkSelf(Manifest.permission.ACCESS_FINE_LOCATION)
            || checkSelf(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private boolean checkSelf(String perm) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return activity.checkSelfPermission(perm)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void addIfMissing(List<String> list, String perm) {
        if (!checkSelf(perm)) list.add(perm);
    }
}
