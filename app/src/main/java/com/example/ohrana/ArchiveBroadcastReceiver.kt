package com.example.ohrana

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Регистратор для запуска архивации данных старше 7 дней
 * Запускается каждый день в 23:55
 */
class ArchiveBroadcastReceiver : BroadcastReceiver() {
    private val TAG = "ArchiveBroadcastReceiver"
    
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast for archive task")
        
        // Проверяем тип действия
        if (intent.action == "android.intent.action.TIME_SET" || 
            intent.action == "android.intent.action.TIMEZONE_CHANGED" ||
            intent.action == "android.intent.action.BOOT_COMPLETED") {
            
            // Запускаем архивацию через корутину
            Thread {
                try {
                    val prefsManager = SharedPrefsManager(context)
                    val archivedCount = prefsManager.archiveOldShifts()
                    
                    Log.d(TAG, "Archive completed: $archivedCount shifts archived")
                } catch (e: Exception) {
                    Log.e(TAG, "Error during archive: ${e.message}", e)
                }
            }.start()
        }
    }
}
