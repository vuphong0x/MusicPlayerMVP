package com.phongvv.musicplayermvp.playback

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import com.phongvv.musicplayermvp.adapter.PlayerAdapter

import com.phongvv.musicplayermvp.models.Song
import com.phongvv.musicplayermvp.service.MusicService
import com.phongvv.musicplayermvp.utils.Utils
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class MediaPlayerHolder(private val musicService: MusicService?) :
    PlayerAdapter, MediaPlayer.OnCompletionListener, MediaPlayer.OnPreparedListener {

    private var context: Context? = null
    private val audioManager: AudioManager
    private var mediaPlayer: MediaPlayer? = null
    private var playbackInfoListener: PlaybackInfoListener? = null
    private var executor: ScheduledExecutorService? = null
    private var seekBarPositionUpdateTask: Runnable? = null
    private var selectedSong: Song? = null
    private var songs: List<Song>? = null
    private var replaySong = false

    @PlaybackInfoListener.State
    private var statusState = 0
    private var notificationActionsReceiver: NotificationReceiver? = null
    private var musicNotificationManager: MusicNotificationManager? = null
    private var currentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
    private var playOnFocusGain: Boolean = false

    private val onAudioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_GAIN -> currentAudioFocusState = AUDIO_FOCUSED

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ->
                    currentAudioFocusState = AUDIO_NO_FOCUS_CAN_DUCK

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    currentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
                    playOnFocusGain =
                        isMediaPlayer() && statusState == PlaybackInfoListener.State.PLAYING || statusState == PlaybackInfoListener.State.RESUMED
                }

                AudioManager.AUDIOFOCUS_LOSS ->
                    currentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
            }

            if (mediaPlayer != null) {
                configurePlayerState()
            }
        }

    init {
        context = musicService?.applicationContext
        audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private fun registerActionsReceiver() {
        notificationActionsReceiver = NotificationReceiver()
        val intentFilter = IntentFilter()

        with(intentFilter) {
            addAction(MusicNotificationManager.PREV_ACTION)
            addAction(MusicNotificationManager.PLAY_PAUSE_ACTION)
            addAction(MusicNotificationManager.NEXT_ACTION)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }

        musicService?.registerReceiver(notificationActionsReceiver, intentFilter)
    }

    private fun unregisterActionsReceiver() {
        if (musicService != null && notificationActionsReceiver != null) {
            try {
                musicService.unregisterReceiver(notificationActionsReceiver)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
        }
    }

    override fun registerNotificationActionsReceiver(isRegister: Boolean) {
        if (isRegister) {
            registerActionsReceiver()
        } else {
            unregisterActionsReceiver()
        }
    }

    override fun getCurrentSong(): Song? {
        return selectedSong
    }


    override fun setCurrentSong(song: Song) {
        selectedSong = song
    }

    override fun onCompletion(mediaPlayer: MediaPlayer) {
        if (playbackInfoListener != null) {
            playbackInfoListener?.onStateChanged(PlaybackInfoListener.State.COMPLETED)
            playbackInfoListener?.onPlaybackCompleted()
        }

        if (replaySong) {
            if (isMediaPlayer()) {
                resetSong()
            }
            replaySong = false
        } else {
            skip(true)
        }
    }

    override fun onResumeActivity() {
        startUpdatingCallbackWithPosition()
    }

    override fun onPauseActivity() {
        stopUpdatingCallbackWithPosition()
    }

    private fun tryToGetAudioFocus() {
        val result = audioManager.requestAudioFocus(
            onAudioFocusChangeListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            currentAudioFocusState = AUDIO_FOCUSED
        } else {
            currentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
        }
    }

    private fun giveUpAudioFocus() {
        if (audioManager.abandonAudioFocus(onAudioFocusChangeListener) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            currentAudioFocusState = AUDIO_NO_FOCUS_NO_DUCK
        }
    }

    override fun setPlaybackInfoListener(playbackInfoListener: PlaybackInfoListener) {
        this.playbackInfoListener = playbackInfoListener
    }

    private fun setStatus(@PlaybackInfoListener.State state: Int) {
        statusState = state
        if (playbackInfoListener != null) {
            playbackInfoListener?.onStateChanged(state)
        }
    }

    private fun resumeMediaPlayer() {
        if (!isPlaying()) {
            mediaPlayer?.start()
            setStatus(PlaybackInfoListener.State.RESUMED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                musicService?.startForeground(
                    MusicNotificationManager.NOTIFICATION_ID,
                    musicNotificationManager?.createNotification()
                )
            }
        }
    }

    private fun pauseMediaPlayer() {
        setStatus(PlaybackInfoListener.State.PAUSED)
        mediaPlayer?.pause()
        musicService?.stopForeground(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            musicNotificationManager?.notificationManager?.notify(
                MusicNotificationManager.NOTIFICATION_ID,
                musicNotificationManager?.createNotification()
            )
        }
    }

    private fun resetSong() {
        mediaPlayer?.seekTo(Utils.NUM_ZERO)
        mediaPlayer?.start()
        setStatus(PlaybackInfoListener.State.PLAYING)
    }

    private fun startUpdatingCallbackWithPosition() {
        if (executor == null) {
            executor = Executors.newSingleThreadScheduledExecutor()
        }
        if (seekBarPositionUpdateTask == null) {
            seekBarPositionUpdateTask = Runnable { updateProgressCallbackTask() }
        }

        executor?.scheduleAtFixedRate(
            seekBarPositionUpdateTask,
            Utils.timeDelay,
            Utils.timePeriod,
            TimeUnit.MILLISECONDS
        )
    }

    private fun stopUpdatingCallbackWithPosition() {
        if (executor != null) {
            executor?.shutdownNow()
            executor = null
            seekBarPositionUpdateTask = null
        }
    }

    private fun updateProgressCallbackTask() {
        if (isMediaPlayer() && mediaPlayer?.isPlaying == true) {
            val currentPosition = mediaPlayer?.currentPosition
            if (playbackInfoListener != null) {
                if (currentPosition != null) {
                    playbackInfoListener?.onPositionChanged(currentPosition)
                }
            }
        }
    }

    override fun instantReset() {
        if (isMediaPlayer()) {
            if (mediaPlayer?.currentPosition!! < Utils.currentPosition) {
                skip(false)
            } else {
                resetSong()
            }
        }
    }

    override fun initMediaPlayer() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer?.reset()
            } else {

                mediaPlayer = MediaPlayer()
                mediaPlayer?.let {
                    it.setOnPreparedListener(this)
                    it.setOnCompletionListener(this)
                    it.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        it.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                    }
                    musicNotificationManager = musicService?.musicNotificationManager
                }
            }

            tryToGetAudioFocus()
            mediaPlayer?.setDataSource(selectedSong?.path)
            mediaPlayer?.prepare()
        } catch (e: Exception) {
            e.printStackTrace()
            skip(true)
        }
    }

    override fun getMediaPlayer(): MediaPlayer? {
        return mediaPlayer
    }

    override fun onPrepared(mediaPlayer: MediaPlayer) {
        startUpdatingCallbackWithPosition()
        setStatus(PlaybackInfoListener.State.PLAYING)
    }

    override fun release() {
        if (isMediaPlayer()) {
            mediaPlayer?.release()
            mediaPlayer = null
            giveUpAudioFocus()
            unregisterActionsReceiver()
        }
    }

    override fun isPlaying(): Boolean {
        return isMediaPlayer() && mediaPlayer?.isPlaying == true
    }

    override fun resumeOrPause() {
        if (isPlaying()) {
            pauseMediaPlayer()
        } else {
            resumeMediaPlayer()
        }
    }

    @PlaybackInfoListener.State
    override fun getState() = statusState

    override fun isMediaPlayer(): Boolean {
        return mediaPlayer != null
    }

    override fun reset() {
        replaySong = !replaySong
    }

    override fun isReset(): Boolean {
        return replaySong
    }

    override fun skip(isNext: Boolean) {
        getSkipSong(isNext)
    }

    private fun getSkipSong(isNext: Boolean) {
        val currentIndex = songs?.indexOf(selectedSong)
        var index: Int? = null

        try {
            if (currentIndex != null) {
                index = if (isNext) currentIndex + Utils.NUM_ONE else currentIndex - Utils.NUM_ONE
            }
            selectedSong = index?.let { songs?.get(it) }
        } catch (e: IndexOutOfBoundsException) {
            selectedSong =
                if (currentIndex != Utils.NUM_ZERO) songs?.first() else songs!![songs!!.size - Utils.NUM_ONE]
            e.printStackTrace()
        }
        initMediaPlayer()
    }

    override fun seekTo(position: Int) {
        if (isMediaPlayer()) {
            mediaPlayer?.seekTo(position)
        }
    }

    override fun getPlayerPosition(): Int? {
        return mediaPlayer?.currentPosition
    }

    private fun configurePlayerState() {

        if (currentAudioFocusState == AUDIO_NO_FOCUS_NO_DUCK) {
            pauseMediaPlayer()
        } else {

            if (currentAudioFocusState == AUDIO_NO_FOCUS_CAN_DUCK) {
                mediaPlayer?.setVolume(VOLUME_DUCK, VOLUME_DUCK)
            } else {
                mediaPlayer?.setVolume(VOLUME_NORMAL, VOLUME_NORMAL)
            }

            if (playOnFocusGain) {
                resumeMediaPlayer()
                playOnFocusGain = false
            }
        }
    }

    private inner class NotificationReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (action != null) {

                when (action) {
                    MusicNotificationManager.PREV_ACTION -> instantReset()
                    MusicNotificationManager.PLAY_PAUSE_ACTION -> resumeOrPause()
                    MusicNotificationManager.NEXT_ACTION -> skip(true)

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (selectedSong != null) {
                        pauseMediaPlayer()
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> if (selectedSong != null && !isPlaying()) {
                        resumeMediaPlayer()
                    }
                    Intent.ACTION_HEADSET_PLUG -> if (selectedSong != null) {
                        when (intent.getIntExtra("state", -1)) {
                            Utils.NUM_ZERO -> pauseMediaPlayer()
                            Utils.NUM_ONE -> if (!isPlaying()) {
                                resumeMediaPlayer()
                            }
                        }
                    }
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> if (isPlaying()) {
                        pauseMediaPlayer()
                    }
                }
            }
        }
    }

    companion object {
        private const val VOLUME_DUCK = 0.2f
        private const val VOLUME_NORMAL = 1.0f
        private const val AUDIO_NO_FOCUS_NO_DUCK = 0
        private const val AUDIO_NO_FOCUS_CAN_DUCK = 1
        private const val AUDIO_FOCUSED = 2
    }
}
