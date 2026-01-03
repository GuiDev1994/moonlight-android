package com.limelight.utils;

import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.widget.TextView;

import com.limelight.R;

import java.util.Locale;

/**
 * Helper class to display current refresh rate in a TextView.
 * Reuses logic from RefreshRatePreference for consistency.
 */
public class RefreshRateDisplayHelper implements Choreographer.FrameCallback {
    private final TextView textView;
    private final Handler mainHandler;
    private Choreographer choreographer;
    private long lastFrameTimeNanos = 0;
    private float currentRefreshRate = 0f;

    public RefreshRateDisplayHelper(TextView textView) {
        this.textView = textView;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.choreographer = Choreographer.getInstance();
    }

    /**
     * Custom rounding logic for refresh rates:
     * - If between 120 and 121, round to 120
     * - If between 117 and 118, round to 118
     * - Otherwise, use standard rounding
     */
    private float roundRefreshRate(float rate) {
        if (rate >= 120.0f && rate < 121.0f) {
            return 120.0f;
        } else if (rate >= 117.0f && rate < 118.0f) {
            return 118.0f;
        } else {
            return Math.round(rate);
        }
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        if (lastFrameTimeNanos > 0) {
            long frameIntervalNs = frameTimeNanos - lastFrameTimeNanos;
            if (frameIntervalNs > 0) {
                float instantRefreshRate = 1_000_000_000f / frameIntervalNs;
                
                // Average over multiple frames for stability
                if (currentRefreshRate == 0f) {
                    currentRefreshRate = instantRefreshRate;
                } else {
                    // Exponential moving average
                    currentRefreshRate = currentRefreshRate * 0.9f + instantRefreshRate * 0.1f;
                }
                
                // Update the text view on the main thread
                updateText();
            }
        }
        
        lastFrameTimeNanos = frameTimeNanos;
        
        // Continue measuring
        choreographer.postFrameCallback(this);
    }

    private void updateText() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (currentRefreshRate > 0) {
                    float roundedRate = roundRefreshRate(currentRefreshRate);
                    String text = String.format(Locale.US, "%.0f Hz", roundedRate);
                    textView.setText(text);
                } else {
                    textView.setText(textView.getContext().getString(R.string.refresh_rate_unavailable));
                }
            }
        });
    }

    public void start() {
        lastFrameTimeNanos = 0;
        currentRefreshRate = 0f;
        choreographer.postFrameCallback(this);
        
        // Set initial text
        textView.setText(textView.getContext().getString(R.string.refresh_rate_unavailable));
    }

    public void stop() {
        choreographer.removeFrameCallback(this);
    }
}
