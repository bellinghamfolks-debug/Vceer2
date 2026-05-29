package com.mokafeefah.clicker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

/**
 * Full-screen translucent overlay that returns the (raw screen) coordinates
 * of the user's next tap. Used to record button positions in external apps —
 * the user navigates to the target screen, opens this activity, then taps
 * the button they want to remember.
 *
 * The activity finishes after the first ACTION_UP with RESULT_OK and the
 * X/Y in the result intent. A cancel button returns RESULT_CANCELED.
 */
public class RecordCoordinateActivity extends Activity {

    public static final String EXTRA_X = "x";
    public static final String EXTRA_Y = "y";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Draw under the status / navigation bars so reported raw coords
        // match what other gestures use (e.g. dispatchGesture in service).
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        setContentView(R.layout.activity_record_coordinate);

        View root = findViewById(R.id.recordRoot);
        root.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == MotionEvent.ACTION_UP) {
                int x = (int) ev.getRawX();
                int y = (int) ev.getRawY();
                vibrateShort();
                Intent result = new Intent();
                result.putExtra(EXTRA_X, x);
                result.putExtra(EXTRA_Y, y);
                setResult(RESULT_OK, result);
                finish();
                return true;
            }
            return true;
        });

        Button cancel = findViewById(R.id.btnCancelRecord);
        cancel.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }

    private void vibrateShort() {
        try {
            Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vib != null && vib.hasVibrator()) vib.vibrate(40);
        } catch (Throwable ignored) { }
    }
}
