package ai.mlxdroid.imagelabarotory.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import ai.mlxdroid.imagelabarotory.MainActivity
import ai.mlxdroid.imagelabarotory.data.local.MediaPipeModelManager
import ai.mlxdroid.imagelabarotory.data.model.ModelDownloadState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ModelDownloadService : Service() {

    @Inject lateinit var modelManager: MediaPipeModelManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isDownloading = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "ACTION_START received")
                startForegroundWithNotification()
                startDownload()
            }
            ACTION_PAUSE -> {
                Log.i(TAG, "ACTION_PAUSE received")
                modelManager.pauseDownload()
                updateNotification("Download paused", -1f)
                // Don't stopSelf — user can resume
            }
            ACTION_RESUME -> {
                Log.i(TAG, "ACTION_RESUME received")
                startDownload()
            }
            ACTION_CANCEL -> {
                Log.i(TAG, "ACTION_CANCEL received")
                modelManager.cancelDownload()
                stopSelf()
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Preparing download...", -1f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startDownload() {
        if (isDownloading) {
            Log.d(TAG, "Download already in progress, ignoring")
            return
        }
        isDownloading = true

        serviceScope.launch {
            // Observe state changes for notification updates
            launch {
                modelManager.downloadState.collect { state ->
                    when (state) {
                        is ModelDownloadState.Downloading -> {
                            updateNotification(
                                "Downloading model... ${(state.progress * 100).toInt()}%",
                                state.progress,
                            )
                        }
                        is ModelDownloadState.Paused -> {
                            updateNotification("Download paused", state.progress, isPaused = true)
                            isDownloading = false
                        }
                        is ModelDownloadState.Ready -> {
                            updateNotification("Model downloaded", 1f, isComplete = true)
                            isDownloading = false
                            stopSelf()
                        }
                        is ModelDownloadState.Error -> {
                            updateNotification("Download failed", -1f, isError = true)
                            isDownloading = false
                            stopSelf()
                        }
                        is ModelDownloadState.NotDownloaded -> {
                            // Cancelled
                            isDownloading = false
                            stopSelf()
                        }
                    }
                }
            }

            // Start the actual download
            modelManager.downloadModel()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Model Download",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows progress for on-device model downloads"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        text: String,
        progress: Float,
        isPaused: Boolean = false,
        isComplete: Boolean = false,
        isError: Boolean = false,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Image Laboratory")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(!isComplete && !isError)

        // Progress bar
        when {
            isComplete || isError -> {
                builder.setSmallIcon(
                    if (isComplete) android.R.drawable.stat_sys_download_done
                    else android.R.drawable.stat_notify_error
                )
            }
            progress >= 0f -> {
                builder.setProgress(100, (progress * 100).toInt(), false)
            }
            else -> {
                builder.setProgress(0, 0, true) // indeterminate
            }
        }

        // Action buttons
        if (!isComplete && !isError) {
            if (isPaused) {
                // Resume button
                val resumeIntent = PendingIntent.getService(
                    this, 1,
                    Intent(this, ModelDownloadService::class.java).apply { action = ACTION_RESUME },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                builder.addAction(
                    Notification.Action.Builder(null, "Resume", resumeIntent).build()
                )
            } else {
                // Pause button
                val pauseIntent = PendingIntent.getService(
                    this, 2,
                    Intent(this, ModelDownloadService::class.java).apply { action = ACTION_PAUSE },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                builder.addAction(
                    Notification.Action.Builder(null, "Pause", pauseIntent).build()
                )
            }

            // Cancel button
            val cancelIntent = PendingIntent.getService(
                this, 3,
                Intent(this, ModelDownloadService::class.java).apply { action = ACTION_CANCEL },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(
                Notification.Action.Builder(null, "Cancel", cancelIntent).build()
            )
        }

        return builder.build()
    }

    private fun updateNotification(
        text: String,
        progress: Float,
        isPaused: Boolean = false,
        isComplete: Boolean = false,
        isError: Boolean = false,
    ) {
        val notification = buildNotification(text, progress, isPaused, isComplete, isError)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ModelDownloadService"
        private const val CHANNEL_ID = "model_download"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ai.mlxdroid.imagelabarotory.action.DOWNLOAD_START"
        const val ACTION_PAUSE = "ai.mlxdroid.imagelabarotory.action.DOWNLOAD_PAUSE"
        const val ACTION_RESUME = "ai.mlxdroid.imagelabarotory.action.DOWNLOAD_RESUME"
        const val ACTION_CANCEL = "ai.mlxdroid.imagelabarotory.action.DOWNLOAD_CANCEL"

        fun start(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startForegroundService(intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, ModelDownloadService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
