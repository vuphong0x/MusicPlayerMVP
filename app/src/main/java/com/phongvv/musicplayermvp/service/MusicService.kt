package com.phongvv.musicplayermvp.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.phongvv.musicplayermvp.playback.MediaPlayerHolder
import com.phongvv.musicplayermvp.playback.MusicNotificationManager

class MusicService : Service() {
    private val iBinder = LocalBinder()
    var mediaPlayerHolder: MediaPlayerHolder? = null
    var musicNotificationManager: MusicNotificationManager? = null
    var isRestoredFromPause = false

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        mediaPlayerHolder?.registerNotificationActionsReceiver(false)
        musicNotificationManager = null
        mediaPlayerHolder?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder {
        if (mediaPlayerHolder == null) {
            mediaPlayerHolder = MediaPlayerHolder(this)
            musicNotificationManager = MusicNotificationManager(this)
            mediaPlayerHolder?.registerNotificationActionsReceiver(true)
        }
        return iBinder
    }

    inner class LocalBinder : Binder() {
        val instance: MusicService
            get() = this@MusicService
    }
}
