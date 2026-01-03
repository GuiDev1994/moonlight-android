package com.limelight.debug;

import android.util.Log;

/**
 * Sistema de log avançado e padronizado para streaming de jogos no Artemis.
 * 
 * Este logger é focado exclusivamente em métricas de streaming, taxa de atualização
 * da tela e desempenho de decode/render.
 * 
 * Os logs são opcionais e podem ser ativados/desativados via flag ENABLE_STREAM_DEBUG_LOG.
 * Para ativar, defina ENABLE_STREAM_DEBUG_LOG = true.
 * 
 * Todos os logs utilizam o prefixo fixo [ARTEMIS_STREAM] no Logcat para fácil filtragem.
 */
public class StreamDebugLogger {
    
    /**
     * Flag global para ativar/desativar os logs de debug do stream.
     * Por padrão, está desativado para evitar overhead e poluição do Logcat.
     */
    public static final boolean ENABLE_STREAM_DEBUG_LOG = false;
    
    /**
     * Prefixo fixo usado em todos os logs para identificação no Logcat.
     */
    private static final String LOG_PREFIX = "[ARTEMIS_STREAM]";
    
    /**
     * Níveis de log disponíveis.
     */
    public enum Level {
        INFO,   // Informações gerais sobre o stream
        WARN,   // Avisos sobre configurações ou condições não ideais
        ERROR,  // Erros críticos relacionados ao streaming
        PERF    // Métricas de performance (decode, render, frame drops, etc)
    }
    
    /**
     * Tag padrão para logs gerais.
     */
    private static final String DEFAULT_TAG = "STREAM";
    
    /**
     * Tag para logs de display/FPS.
     */
    public static final String TAG_DISPLAY = "DISPLAY";
    
    /**
     * Tag para logs de configuração do stream.
     */
    public static final String TAG_STREAM = "STREAM";
    
    /**
     * Tag para logs de decoder/performance.
     */
    public static final String TAG_DECODER = "DECODER";
    
    /**
     * Método base de log.
     * 
     * @param level Nível do log (INFO, WARN, ERROR, PERF)
     * @param tag Tag adicional para categorização (ex: DISPLAY, STREAM, DECODER)
     * @param message Mensagem a ser logada
     */
    public static void log(Level level, String tag, String message) {
        // Se o log estiver desativado, retorna imediatamente sem overhead
        if (!ENABLE_STREAM_DEBUG_LOG) {
            return;
        }
        
        // Formata a mensagem com prefixo fixo e tag
        String formattedMessage = String.format("%s[%s] %s", LOG_PREFIX, tag, message);
        
        // Escolhe o método de log apropriado baseado no nível
        switch (level) {
            case INFO:
                Log.i(DEFAULT_TAG, formattedMessage);
                break;
            case WARN:
                Log.w(DEFAULT_TAG, formattedMessage);
                break;
            case ERROR:
                Log.e(DEFAULT_TAG, formattedMessage);
                break;
            case PERF:
                // Performance logs também são INFO no Android Log, mas com tag PERF
                Log.i(DEFAULT_TAG, formattedMessage);
                break;
        }
    }
    
    /**
     * Método conveniente para log INFO.
     */
    public static void info(String tag, String message) {
        log(Level.INFO, tag, message);
    }
    
    /**
     * Método conveniente para log WARN.
     */
    public static void warn(String tag, String message) {
        log(Level.WARN, tag, message);
    }
    
    /**
     * Método conveniente para log ERROR.
     */
    public static void error(String tag, String message) {
        log(Level.ERROR, tag, message);
    }
    
    /**
     * Método conveniente para log PERF.
     */
    public static void perf(String tag, String message) {
        log(Level.PERF, tag, message);
    }
}
