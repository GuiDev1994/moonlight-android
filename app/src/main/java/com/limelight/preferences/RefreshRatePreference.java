package com.limelight.preferences;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.limelight.R;
import com.limelight.debug.StreamDebugLogger;

import java.util.Locale;

public class RefreshRatePreference extends Preference implements Choreographer.FrameCallback {
    private Choreographer choreographer;
    private long lastFrameTimeNanos = 0;
    private float currentRefreshRate = 0f;
    private static float staticRefreshRate = 0f;
    
    // Runtime refresh rate detection - listener e estado
    private static DisplayManager.DisplayListener displayListener = null;
    private static DisplayManager displayManager = null;
    private static Handler backgroundHandler = null;
    private static HandlerThread backgroundThread = null;
    private static boolean isListenerRegistered = false;
    private static float lastDetectedRefreshRate = 0f;
    private static float lastRoundedFps = 0f;
    private static long lastUpdateTimeMs = 0L;
    private static final float MIN_DELTA_HZ = 0.3f; // Diferença mínima para considerar mudança significativa
    private static final long MIN_UPDATE_INTERVAL_MS = 1500L; // Intervalo mínimo entre atualizações

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

    private void updateSummary() {
        if (currentRefreshRate > 0) {
            float roundedRate = roundRefreshRate(currentRefreshRate);
            // Display the rounded value (integer) instead of decimal
            String summary = String.format(Locale.US, "%.0f Hz", roundedRate);
            setSummary(summary);
            staticRefreshRate = roundedRate;
        } else {
            setSummary(getContext().getString(R.string.refresh_rate_unavailable));
        }
    }

    /**
     * Custom rounding logic for refresh rates:
     * - If between 120 and 121, round to 120
     * - If between 117 and 118, round to 118
     * - Otherwise, use standard rounding
     */
    private static float roundRefreshRateStatic(float rate) {
        if (rate >= 120.0f && rate < 121.0f) {
            return 120.0f;
        } else if (rate >= 117.0f && rate < 118.0f) {
            return 118.0f;
        } else {
            return Math.round(rate);
        }
    }

    /**
     * Updates the static refresh rate from an external measurement.
     * This allows RefreshRateDisplayHelper and other components to share
     * their measured refresh rate with getCurrentRefreshRateSync().
     * 
     * @param measuredRate The refresh rate measured in Hz (will be rounded)
     */
    public static void updateStaticRefreshRate(float measuredRate) {
        if (measuredRate > 0) {
            staticRefreshRate = roundRefreshRateStatic(measuredRate);
        }
    }

    /**
     * Gets the current refresh rate synchronously.
     * If the preference has measured a refresh rate, returns that value.
     * Otherwise, falls back to the current refresh rate from Display.
     * 
     * @param context The context to get the display from
     * @return The refresh rate in Hz (rounded according to custom logic), or 0 if unavailable
     */
    public static float getCurrentRefreshRateSync(Context context) {
        if (staticRefreshRate > 0) {
            return staticRefreshRate;
        }
        
        // Fallback to current display refresh rate (not maximum supported)
        try {
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null) {
                Display display = wm.getDefaultDisplay();
                if (display != null) {
                    // Use the current refresh rate, not the maximum supported
                    float currentRefreshRate = display.getRefreshRate();
                    if (currentRefreshRate > 0) {
                        return roundRefreshRateStatic(currentRefreshRate);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return 0f;
    }
    
    /**
     * Detecta o refresh rate atual do display usando a mesma lógica de getCurrentRefreshRateSync.
     * Este método é reutilizado para detecção em runtime quando o display muda.
     * 
     * @param context Context para acessar o DisplayManager
     * @param displayId ID do display a ser verificado (ou Display.DEFAULT_DISPLAY)
     * @return Refresh rate detectado em Hz (arredondado), ou 0 se indisponível
     */
    private static float detectCurrentRefreshRate(Context context, int displayId) {
        try {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (dm != null) {
                Display display = dm.getDisplay(displayId);
                if (display != null) {
                    // Para detecção em runtime, usamos o refresh rate atual do display,
                    // não o máximo suportado. Isso permite detectar mudanças reais em tempo real.
                    float currentRate = display.getRefreshRate();
                    return roundRefreshRateStatic(currentRate);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        
        return 0f;
    }
    
    /**
     * Processa mudança de refresh rate detectada.
     * Compara com o último valor conhecido e aplica atualização se significativa.
     * 
     * @param context Context para logging
     * @param newRefreshRate Novo refresh rate detectado
     */
    private static void handleRefreshRateChange(Context context, float newRefreshRate) {
        if (newRefreshRate <= 0f) {
            return; // Ignora valores inválidos
        }
        
        long now = System.currentTimeMillis();
        float delta = Math.abs(newRefreshRate - lastDetectedRefreshRate);
        float roundedFps = roundRefreshRateStatic(newRefreshRate);
        
        // Proteção contra atualizações muito frequentes ou mudanças insignificantes
        boolean shouldUpdate = false;
        if (lastDetectedRefreshRate == 0f) {
            // Primeira detecção - sempre atualiza
            shouldUpdate = true;
        } else if (delta >= MIN_DELTA_HZ && (now - lastUpdateTimeMs) >= MIN_UPDATE_INTERVAL_MS) {
            // Mudança significativa e intervalo suficiente desde última atualização
            shouldUpdate = true;
        }
        
        if (shouldUpdate) {
            float oldRate = lastDetectedRefreshRate;
            float oldRounded = lastRoundedFps;
            
            lastDetectedRefreshRate = newRefreshRate;
            lastRoundedFps = roundedFps;
            lastUpdateTimeMs = now;
            staticRefreshRate = roundedFps; // Atualiza o valor estático usado por getCurrentRefreshRateSync
            
            // Log da mudança detectada
            StreamDebugLogger.info(StreamDebugLogger.TAG_DISPLAY,
                String.format(Locale.US, "Refresh rate changed: %.2f Hz -> %.2f Hz", oldRate, newRefreshRate));
            StreamDebugLogger.info(StreamDebugLogger.TAG_DISPLAY,
                String.format(Locale.US, "Rounded FPS recalculated: %.0f", roundedFps));
        } else {
            // Log quando mudança é ignorada (para debug)
            if (delta < MIN_DELTA_HZ) {
                StreamDebugLogger.info(StreamDebugLogger.TAG_DISPLAY,
                    String.format(Locale.US, "Update ignored (delta too small: %.2f Hz)", delta));
            } else {
                StreamDebugLogger.info(StreamDebugLogger.TAG_DISPLAY,
                    String.format(Locale.US, "Update ignored (too soon: %d ms)", (now - lastUpdateTimeMs)));
            }
        }
    }
    
    /**
     * Registra o listener para detectar mudanças de display em runtime.
     * Deve ser chamado quando o stream iniciar para começar a monitorar mudanças.
     * 
     * Este método cria um HandlerThread em background para processar eventos do DisplayManager
     * sem bloquear a UI thread.
     * 
     * @param context Context para acessar o DisplayManager (deve ter Application scope)
     */
    public static void registerDisplayChangeListener(Context context) {
        // Evita múltiplos listeners registrados
        if (isListenerRegistered) {
            return;
        }
        
        try {
            DisplayManager dm = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (dm == null) {
                return;
            }
            
            // Inicializa thread em background para processar eventos
            if (backgroundThread == null) {
                backgroundThread = new HandlerThread("RefreshRateDetection");
                backgroundThread.start();
                backgroundHandler = new Handler(backgroundThread.getLooper());
            }
            
            // Captura ApplicationContext para uso seguro nos callbacks
            final Context appContext = context.getApplicationContext();
            
            // Cria o listener que reutiliza a lógica existente de detecção
            displayListener = new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {
                    // Quando um display é adicionado, pode ser um display externo
                    // Não processamos aqui pois queremos focar no display principal do stream
                    // Este evento é ignorado para evitar processamento desnecessário
                }
                
                @Override
                public void onDisplayRemoved(int displayId) {
                    // Quando o display é removido (ex: desconexão de display externo),
                    // não precisamos recalcular se o display principal ainda está ativo
                    // Este evento é ignorado para evitar processamento desnecessário
                }
                
                @Override
                public void onDisplayChanged(int displayId) {
                    // Este é o evento principal que indica mudança de configuração do display
                    // (incluindo refresh rate). Processamos em background thread para não bloquear.
                    backgroundHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            // Reutiliza a mesma lógica de detecção existente
                            // Usa appContext para garantir que sempre temos um context válido
                            float newRate = detectCurrentRefreshRate(appContext, Display.DEFAULT_DISPLAY);
                            if (newRate > 0f) {
                                handleRefreshRateChange(appContext, newRate);
                            }
                        }
                    });
                }
            };
            
            // Registra o listener no DisplayManager usando o HandlerThread
            // Usamos o HandlerThread para garantir que callbacks não executem na UI thread
            dm.registerDisplayListener(displayListener, backgroundHandler);
            
            displayManager = dm;
            isListenerRegistered = true;
            
            // Detecta o refresh rate inicial ao registrar
            float initialRate = detectCurrentRefreshRate(context, Display.DEFAULT_DISPLAY);
            if (initialRate > 0f) {
                lastDetectedRefreshRate = initialRate;
                lastRoundedFps = roundRefreshRateStatic(initialRate);
                staticRefreshRate = lastRoundedFps;
            }
            
        } catch (Exception e) {
            // Em caso de erro, limpa o estado para evitar vazamentos
            isListenerRegistered = false;
            displayListener = null;
            displayManager = null;
        }
    }
    
    /**
     * Remove o listener de mudanças de display.
     * Deve ser chamado quando o stream parar para evitar vazamentos de memória.
     */
    public static void unregisterDisplayChangeListener() {
        if (!isListenerRegistered || displayManager == null || displayListener == null) {
            return;
        }
        
        try {
            displayManager.unregisterDisplayListener(displayListener);
            isListenerRegistered = false;
            displayListener = null;
            // Mantemos displayManager e backgroundHandler para possível reuso
        } catch (Exception e) {
            // Ignore
        }
    }
    
    /**
     * Limpa completamente os recursos do listener (chamado no onDestroy).
     * Encerra a thread em background e libera todos os recursos.
     */
    public static void cleanupDisplayChangeListener() {
        unregisterDisplayChangeListener();
        
        if (backgroundThread != null) {
            try {
                backgroundThread.quitSafely();
                backgroundThread.join(500); // Aguarda até 500ms para encerrar
            } catch (InterruptedException e) {
                // Ignore
            }
            backgroundThread = null;
            backgroundHandler = null;
        }
        
        displayManager = null;
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
