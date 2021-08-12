package com.phongvv.musicplayermvp.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import com.phongvv.musicplayermvp.R
import com.phongvv.musicplayermvp.models.Song
import com.phongvv.musicplayermvp.service.MusicService
import com.phongvv.musicplayermvp.ui.MainActivity

class MusicNotificationManager internal constructor(private val musicService: MusicService) {

    val notificationManager: NotificationManager
    var notificationBuilder: NotificationCompat.Builder? = null
    private var mediaSession: MediaSessionCompat? = null
    private var mediaSessionManager: MediaSessionManager? = null
    private var transportControls: MediaControllerCompat.TransportControls? = null
    private val context: Context

    init {
        notificationManager =
            musicService.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        context = musicService.baseContext
    }

    private fun playerAction(action: String): PendingIntent {
        val pauseIntent = Intent()
        pauseIntent.action = action

        return PendingIntent.getBroadcast(
            musicService,
            REQUEST_CODE,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun createNotification(): Notification? {

        val song = musicService.mediaPlayerHolder?.getCurrentSong()

        notificationBuilder = NotificationCompat.Builder(musicService, CHANNEL_ID)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }

        val openPlayerIntent = Intent(musicService, MainActivity::class.java)
        openPlayerIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val contentIntent = PendingIntent.getActivity(
            musicService, REQUEST_CODE,
            openPlayerIntent, 0
        )

        val artist = song?.artistName
        val songTitle = song?.title

        song?.let { initMediaSession(it) }

        notificationBuilder?.run {
            setShowWhen(false)
            setSmallIcon(R.drawable.ic_play)
            color = ContextCompat.getColor(context, R.color.color_mauvelous)
            setContentTitle(songTitle)
            setContentText(artist)
            setContentIntent(contentIntent)
            addAction(notificationAction(PREV_ACTION))
            addAction(notificationAction(PLAY_PAUSE_ACTION))
            addAction(notificationAction(NEXT_ACTION))
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        }

        notificationBuilder?.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
        )
        return notificationBuilder?.build()
    }

    private fun notificationAction(action: String): NotificationCompat.Action {
        val icon: Int = when (action) {
            PREV_ACTION -> R.drawable.ic_previous
            PLAY_PAUSE_ACTION ->

                if (musicService.mediaPlayerHolder?.getState() != PlaybackInfoListener.State.PAUSED)
                    R.drawable.ic_pause
                else
                    R.drawable.ic_play
            NEXT_ACTION -> R.drawable.ic_next
            else -> R.drawable.ic_previous
        }
        return NotificationCompat.Action.Builder(icon, action, playerAction(action)).build()
    }

    @RequiresApi(26)
    private fun createNotificationChannel() {

        if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                musicService.getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationChannel.run {
                description = musicService.getString(R.string.app_name)
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
            }

            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun initMediaSession(song: Song) {
        mediaSessionManager =
            context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        mediaSession = MediaSessionCompat(context, "AudioPlayer")
        transportControls = mediaSession?.controller?.transportControls
        mediaSession?.isActive = true
        mediaSession?.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
        updateMetaData(song)
    }

    private fun updateMetaData(song: Song) {
        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artistName)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.albumName)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                .build()
        )
    }

    companion object {
        const val REQUEST_CODE = 100
        const val NOTIFICATION_ID = 101
        const val CHANNEL_ID = "action.CHANNEL_ID"
        const val PLAY_PAUSE_ACTION = "action.PLAYPAUSE"
        const val NEXT_ACTION = "action.NEXT"
        const val PREV_ACTION = "action.PREV"
    }
}
