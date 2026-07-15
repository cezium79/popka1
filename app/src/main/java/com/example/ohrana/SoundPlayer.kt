package com.example.ohrana

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.util.Log

/**
 * Утилита для воспроизведения звуков событий
 * Использует системные звуки Android для минимизации зависимостей
 */
object SoundPlayer {
    private const val TAG = "SoundPlayer"
    
    /**
     * Инициализация звуков
     */
    fun init(context: Context) {
        Log.d(TAG, "SoundPlayer.init()")
        Log.d(TAG, "SoundPlayer initialized with system sounds")
    }
    
    /**
     * Освобождение ресурсов
     */
    fun release() {
        Log.d(TAG, "SoundPlayer released")
    }
    
    /**
     * Проверить, включен ли звук для события
     */
    fun shouldPlaySound(context: Context, eventId: Int): Boolean {
        val prefsManager = SharedPrefsManager(context)
        
        // Сначала проверяем общую настройку звука
        if (!prefsManager.isSoundEnabled()) {
            return false
        }
        
        // Затем проверяем настройку конкретного события
        return prefsManager.isSoundEnabledForEvent(eventId)
    }
    
    /**
     * Воспроизвести звук успеха (событие 1)
     */
    fun playSuccess(context: Context) {
        playSound(context, 1, "success")
    }
    
    /**
     * Воспроизвести звук ошибки (событие 2)
     */
    fun playError(context: Context) {
        playSound(context, 2, "error")
    }
    
    /**
     * Воспроизвести звук предупреждения (событие 3)
     */
    fun playWarning(context: Context) {
        playSound(context, 3, "warning")
    }
    
    /**
     * Воспроизвести звук по ID события
     */
    private fun playSound(context: Context, eventId: Int, soundName: String) {
        if (!shouldPlaySound(context, eventId)) {
            Log.d(TAG, "Sound $soundName disabled for event $eventId")
            return
        }
        
        val prefsManager = SharedPrefsManager(context)
        var uri = prefsManager.getEventSoundUri(eventId)
        
        if (uri == null) {
            Log.e(TAG, "Sound URI is null for $soundName")
            uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
        
        try {
            // Используем MediaPlayer для воспроизведения звука
            val mediaPlayer = MediaPlayer.create(context, uri)
            mediaPlayer?.setOnCompletionListener {
                it.release()
            }
            mediaPlayer?.start()
            
            Log.d(TAG, "Played sound: $soundName (event $eventId)")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing sound $soundName: ${e.message}", e)
        }
    }
    
    /**
     * Воспроизвести звук по ID события с кастомным URI
     */
    fun playCustomSound(context: Context, eventId: Int, customUri: android.net.Uri) {
        if (!shouldPlaySound(context, eventId)) {
            Log.d(TAG, "Custom sound disabled for event $eventId")
            return
        }
        
        try {
            val mediaPlayer = MediaPlayer.create(context, customUri)
            mediaPlayer?.setOnCompletionListener {
                it.release()
            }
            mediaPlayer?.start()
            
            Log.d(TAG, "Played custom sound for event $eventId")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing custom sound: ${e.message}", e)
        }
    }
}
