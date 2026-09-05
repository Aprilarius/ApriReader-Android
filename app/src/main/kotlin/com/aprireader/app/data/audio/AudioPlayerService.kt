package com.aprireader.app.data.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aprireader.app.MainActivity
import com.aprireader.app.R
import com.aprireader.app.appContainer
import java.io.File

class AudioPlayerService : Service() {

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val player = applicationContext.appContainer.audioPlayer

        when (action) {
            ACTION_PLAY -> player.play()
            ACTION_PAUSE -> player.pause()
            ACTION_TOGGLE -> player.togglePlayPause()
            ACTION_REWIND_15 -> player.seekBy(-15_000L)
            ACTION_FORWARD_15 -> player.seekBy(15_000L)
            ACTION_STOP -> {
                player.pause()
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val state = player.state.value
                val book = state.currentBook
                if (book != null) {
                    val notification = buildNotification(state)
                    if (state.isPlaying) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            startForeground(
                                NOTIFICATION_ID,
                                notification,
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                            )
                        } else {
                            startForeground(NOTIFICATION_ID, notification)
                        }
                    } else {
                        notificationManager.notify(NOTIFICATION_ID, notification)
                    }
                } else {
                    stopForegroundService()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val player = applicationContext.appContainer.audioPlayer
        // pauseAndRelease(), не pause()+releasePlayer(): pause() бьёт по
        // слушателю плеера, а тот сам перезапускает этот же сервис через
        // AudioPlayerService.startOrUpdate — гонка со stopSelf() ниже могла
        // отменить остановку сервиса. См. комментарий у pauseAndRelease().
        player.pauseAndRelease()
        stopForegroundService()
    }

    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Аудиокниги ApriReader",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Управление воспроизведением аудиокниг"
                setShowBadge(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(state: AudioPlayerState): Notification {
        val book = state.currentBook
        val title = book?.title ?: "Аудиокнига"
        val author = book?.authorLine ?: ""

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val rewindIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, AudioPlayerService::class.java).apply { action = ACTION_REWIND_15 },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, AudioPlayerService::class.java).apply {
                action = if (state.isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val forwardIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, AudioPlayerService::class.java).apply { action = ACTION_FORWARD_15 },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, AudioPlayerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val coverBitmap = book?.coverPath?.let { path ->
            runCatching {
                val file = File(path)
                if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
            }.getOrNull()
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(author)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(state.isPlaying)
            .addAction(android.R.drawable.ic_media_rew, "-15s", rewindIntent)
            .addAction(
                if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (state.isPlaying) "Пауза" else "Воспроизведение",
                playPauseIntent,
            )
            .addAction(android.R.drawable.ic_media_ff, "+15s", forwardIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Закрыть", stopIntent)

        if (coverBitmap != null) {
            builder.setLargeIcon(coverBitmap)
        }

        return builder.build()
    }

    companion object {
        const val CHANNEL_ID = "audiobook_playback_channel"
        const val NOTIFICATION_ID = 4001

        const val ACTION_PLAY = "com.aprireader.app.action.AUDIO_PLAY"
        const val ACTION_PAUSE = "com.aprireader.app.action.AUDIO_PAUSE"
        const val ACTION_TOGGLE = "com.aprireader.app.action.AUDIO_TOGGLE"
        const val ACTION_REWIND_15 = "com.aprireader.app.action.AUDIO_REWIND_15"
        const val ACTION_FORWARD_15 = "com.aprireader.app.action.AUDIO_FORWARD_15"
        const val ACTION_STOP = "com.aprireader.app.action.AUDIO_STOP"
        const val ACTION_UPDATE = "com.aprireader.app.action.AUDIO_UPDATE"

        fun startOrUpdate(context: Context, state: AudioPlayerState) {
            val intent = Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_UPDATE
            }
            if (state.isPlaying) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching { context.startForegroundService(intent) }
                } else {
                    runCatching { context.startService(intent) }
                }
            } else {
                runCatching { context.startService(intent) }
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioPlayerService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
