package com.example.blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class RestartReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "RestartReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        try {
            Log.d(TAG, "Received ${intent?.action}, starting service")
            val start = Intent(context, AppMonitorService::class.java)
            ContextCompat.startForegroundService(context, start)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service from receiver", e)
        }
    }
}
