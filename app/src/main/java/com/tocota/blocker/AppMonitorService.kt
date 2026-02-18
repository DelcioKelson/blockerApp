package com.tocota.blocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.AlarmManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class AppMonitorService : Service() {

    companion object {
        private const val TAG = "AppMonitorService"
        private const val CHANNEL_ID = "blocker_channel"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MS = 500L
        private const val BLOCK_COOLDOWN_MS = 2000L
        private const val QUERY_WINDOW_MS = 5000L
        
        @Volatile
        var isRunning = false
            private set

        /**
         * Mark service as not running from outside (UI requested stop).
         * Use sparingly — this helps the UI reflect state immediately after stopService()
         */
        fun markNotRunning() {
            isRunning = false
        }

        /**
         * Mark service as running from outside (UI requested start).
         * This lets the UI reflect the running state immediately while the
         * system starts the service in the background.
         */
        fun markRunning() {
            isRunning = true
        }

        /**
         * Cancel any scheduled restart Alarm set in onTaskRemoved().
         */
        fun cancelScheduledRestart(ctx: Context) {
            try {
                val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                val restartIntent = Intent(ctx, AppMonitorService::class.java)
                val pending = PendingIntent.getService(
                    ctx,
                    1,
                    restartIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (pending != null) {
                    am?.cancel(pending)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error cancelling scheduled restart", e)
            }
        }
    }

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var usageStatsManager: UsageStatsManager? = null
    private val lastBlockedTime = AtomicLong(0L)
    private val isPolling = AtomicBoolean(false)

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isPolling.get()) return
            
            try {
                checkForegroundApp()
            } catch (e: Exception) {
                Log.e(TAG, "Error checking foreground app", e)
            }
            
            handler?.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        try {
            // Create background thread for polling
            handlerThread = HandlerThread("AppMonitorThread", Process.THREAD_PRIORITY_BACKGROUND).apply {
                start()
            }
            handler = Handler(handlerThread!!.looper)
            
            usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usageStatsManager == null) {
                Log.e(TAG, "UsageStatsManager not available")
                stopSelf()
                return
            }
            
            startForegroundServiceWithNotification()
            isPolling.set(true)
            isRunning = true
            handler?.post(pollRunnable)
            Log.d(TAG, "Service created and monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure polling is running
        if (!isPolling.get() && handler != null) {
            isPolling.set(true)
            handler?.post(pollRunnable)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        isPolling.set(false)
        
        handler?.removeCallbacksAndMessages(null)
        handlerThread?.quitSafely()
        
        handler = null
        handlerThread = null
        usageStatsManager = null
        
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        try {
            // Schedule a restart in 1s using AlarmManager to make stopping the service harder
            val restartIntent = Intent(applicationContext, AppMonitorService::class.java)
            val pending = PendingIntent.getService(
                applicationContext,
                1,
                restartIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val am = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1000L, pending)
            Log.d(TAG, "Scheduled restart after task removal")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling restart on task removed", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundServiceWithNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Blocker Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when the app blocker is running"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BlockerApp Active")
            .setContentText("Monitoring apps in background")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground", e)
        }
    }

    private fun checkForegroundApp() {
        val statsManager = usageStatsManager ?: return
        
        val end = System.currentTimeMillis()
        val begin = end - QUERY_WINDOW_MS

        val events = try {
            statsManager.queryEvents(begin, end)
        } catch (e: Exception) {
            Log.w(TAG, "Error querying events", e)
            return
        }
        
        if (events == null) return

        var lastForegroundPkg: String? = null
        var lastEventTime = 0L
        val event = UsageEvents.Event()

        try {
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val eventType = event.eventType
                val pkg = event.packageName ?: continue
                
                @Suppress("DEPRECATION")
                val isForeground = eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                    eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    eventType == 1
                
                if (isForeground && event.timeStamp > lastEventTime) {
                    lastForegroundPkg = pkg
                    lastEventTime = event.timeStamp
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error processing events", e)
            return
        }

        if (lastForegroundPkg == null || lastForegroundPkg == packageName) {
            return
        }

        val blocked = try {
            BlockPreferences.getBlockedPackages(this)
        } catch (e: Exception) {
            Log.w(TAG, "Error getting blocked packages", e)
            return
        }
        
        if (blocked.contains(lastForegroundPkg)) {
            val now = System.currentTimeMillis()
            val lastBlocked = lastBlockedTime.get()
            
            if (now - lastBlocked > BLOCK_COOLDOWN_MS) {
                if (lastBlockedTime.compareAndSet(lastBlocked, now)) {
                    Log.i(TAG, "BLOCKING app: $lastForegroundPkg")
                    launchBlockedActivity(lastForegroundPkg)
                }
            }
        }
    }

    private fun launchBlockedActivity(blockedApp: String) {
        try {
            val intent = Intent(this, BlockedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("blocked_type", "app")
                putExtra("blocked_name", blockedApp)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching blocked activity", e)
        }
    }
}
