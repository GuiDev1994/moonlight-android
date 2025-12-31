package com.limelight.preferences;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.limelight.R;

import java.util.Locale;

public class RefreshRatePreference extends Preference implements Choreographer.FrameCallback {
    private Choreographer choreographer;
    private long lastFrameTimeNanos = 0;
    private float currentRefreshRate = 0f;
    private static float staticRefreshRate = 0f;

    public RefreshRatePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize();
    }

    public RefreshRatePreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    public RefreshRatePreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    private void initialize() {
        setSelectable(false); // Make it non-clickable, just for display
        choreographer = Choreographer.getInstance();
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
                
                // Update the summary on the main thread
                updateSummary();
            }
        }
        
        lastFrameTimeNanos = frameTimeNanos;
        
        // Continue measuring
        choreographer.postFrameCallback(this);
    }

    private void updateSummary() {
        if (currentRefreshRate > 0) {
            String summary = String.format(Locale.US, "%.2f Hz", currentRefreshRate);
            setSummary(summary);
            staticRefreshRate = currentRefreshRate;
        } else {
            setSummary(getContext().getString(R.string.refresh_rate_unavailable));
        }
    }

    /**
     * Gets the current refresh rate synchronously.
     * If the preference has measured a refresh rate, returns that value.
     * Otherwise, falls back to the maximum supported refresh rate from Display.
     * 
     * @param context The context to get the display from
     * @return The refresh rate in Hz, or 0 if unavailable
     */
    public static float getCurrentRefreshRateSync(Context context) {
        if (staticRefreshRate > 0) {
            return staticRefreshRate;
        }
        
        // Fallback to maximum display refresh rate
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                Display display = wm.getDefaultDisplay();
                if (display != null) {
                    float maxRefreshRate = display.getRefreshRate();
                    
                    // On Android M+, try to get the maximum refresh rate from supported modes
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        Display.Mode[] modes = display.getSupportedModes();
                        if (modes != null && modes.length > 0) {
                            for (Display.Mode mode : modes) {
                                float refreshRate = mode.getRefreshRate();
                                if (refreshRate > maxRefreshRate) {
                                    maxRefreshRate = refreshRate;
                                }
                            }
                        }
                    } else {
                        // On older Android versions, try to get max from supported refresh rates
                        float[] supportedRates = display.getSupportedRefreshRates();
                        if (supportedRates != null && supportedRates.length > 0) {
                            for (float rate : supportedRates) {
                                if (rate > maxRefreshRate) {
                                    maxRefreshRate = rate;
                                }
                            }
                        }
                    }
                    
                    return maxRefreshRate;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return 0f;
    }

    @Override
    public void onAttached() {
        super.onAttached();
        // Start measuring when attached
        lastFrameTimeNanos = 0;
        currentRefreshRate = 0f;
        choreographer.postFrameCallback(this);
        
        // Set initial summary
        setSummary(getContext().getString(R.string.refresh_rate_unavailable));
    }

    @Override
    public void onDetached() {
        super.onDetached();
        // Stop measuring when detached
        choreographer.removeFrameCallback(this);
    }
}
