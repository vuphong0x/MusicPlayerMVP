package com.phongvv.musicplayermvp.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.phongvv.musicplayermvp.R
import com.phongvv.musicplayermvp.base.BaseActivity
import com.phongvv.musicplayermvp.databinding.ActivityMainBinding
import com.phongvv.musicplayermvp.models.Song
import com.phongvv.musicplayermvp.playback.MusicNotificationManager
import com.phongvv.musicplayermvp.service.MusicService
import com.phongvv.musicplayermvp.playback.PlaybackInfoListener
import com.phongvv.musicplayermvp.adapter.PlayerAdapter
import com.phongvv.musicplayermvp.adapter.SongAdapter
import com.phongvv.musicplayermvp.utils.Utils

class MainActivity : BaseActivity<ActivityMainBinding>(),
    SongContract.View,
    View.OnClickListener {

    override val bindingInflater: (LayoutInflater) -> ActivityMainBinding
        get() = ActivityMainBinding::inflate
    private var presenter: SongPresenter? = null
    private var musicService: MusicService? = null
    private var isBound: Boolean? = null
    private var playerAdapter: PlayerAdapter? = null
    private var userIsSeeking = false
    private var playbackListener: PlaybackListener? = null
    private var musicNotificationManager: MusicNotificationManager? = null
    private var songAdapter = SongAdapter(this::onSongClick)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {

            musicService = (iBinder as MusicService.LocalBinder).instance
            playerAdapter = musicService?.mediaPlayerHolder
            musicNotificationManager = musicService?.musicNotificationManager

            if (playbackListener == null) {
                playbackListener = PlaybackListener()
                playerAdapter?.setPlaybackInfoListener(playbackListener!!)
            }
            if (playerAdapter != null && playerAdapter?.isPlaying() == true) {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    restorePlayerStatus()
                }
            }
            checkReadStoragePermissions()
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            musicService = null
        }
    }

    override fun initComponents() {
        if (checkReadStoragePermissions()) {
            doBindService()
            initViews()
            initData()
            initAdapter()
            initializeSeekBar()
        }
    }

    override fun onPause() {
        super.onPause()
        doUnbindService()
        if (playerAdapter != null && playerAdapter?.isMediaPlayer() == true) {
            playerAdapter?.onPauseActivity()
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onResume() {
        super.onResume()
        doBindService()
        if (playerAdapter != null && playerAdapter?.isPlaying() == true) {
            restorePlayerStatus()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Utils.NUM_ONE && grantResults.isNotEmpty() && grantResults.first() == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onClick(v: View) = with(binding) {
        when (v) {
            buttonPlayPause -> playOrPause()
            buttonNext -> skipNext()
            buttonPrevious -> skipPrevious()
        }
    }

    override fun showAllSongs(songs: List<Song>) {
        songAdapter.updateData(songs)
    }

    override fun showError(error: String) {
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    private fun initViews() {
        binding.run {
            listOf(
                buttonPlayPause,
                buttonPrevious,
                buttonNext
            ).forEach {
                it.setOnClickListener(this@MainActivity)
            }
        }
    }

    private fun initData() {
        presenter = SongPresenter(this, Utils.getSongRepository(contentResolver))
        presenter?.start()
    }

    private fun initAdapter() {
        binding.recyclerSongs.adapter = songAdapter
    }

    private fun initializeSeekBar() {
        binding.seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                var userSelectedPosition = 0

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    userIsSeeking = true
                }

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        userSelectedPosition = progress
                    }
                    binding.textTimePosition.text = Utils.formatDuration(progress)
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    userIsSeeking = false
                    playerAdapter?.seekTo(userSelectedPosition)
                }
            })
    }

    private fun checkReadStoragePermissions(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), Utils.NUM_ONE
            )
            false
        } else true
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun updatePlayingInfo(restore: Boolean, startPlay: Boolean) {

        if (startPlay) {
            playerAdapter?.getMediaPlayer()?.start()
            Handler().postDelayed({
                musicService?.startForeground(
                    MusicNotificationManager.NOTIFICATION_ID,
                    musicNotificationManager?.createNotification()
                )
            }, Utils.delayMillis)
        }

        val selectedSong = playerAdapter?.getCurrentSong()
        val duration = selectedSong?.duration
        if (duration != null) {
            binding.seekBar.max = duration
        }
        binding.run {
            textSongName.text = selectedSong?.title
            textArtist.text = selectedSong?.artistName
            textDuration.text = duration?.let { Utils.formatDuration(it) }
        }

        if (restore) {
            binding.seekBar.progress = playerAdapter?.getPlayerPosition()!!

            updatePlayingStatus()
            Handler().postDelayed({

                if (musicService?.isRestoredFromPause == true) {
                    musicService?.stopForeground(false)
                    musicService?.musicNotificationManager?.notificationManager
                        ?.notify(
                            MusicNotificationManager.NOTIFICATION_ID,
                            musicService?.musicNotificationManager?.notificationBuilder?.build()
                        )
                    musicService?.isRestoredFromPause = false
                }
            }, Utils.delayMillis)
        }
    }

    private fun updatePlayingStatus() {
        val drawable = if (playerAdapter?.getState() != PlaybackInfoListener.State.PAUSED)
            R.drawable.ic_pause
        else
            R.drawable.ic_play
        binding.buttonPlayPause.post { binding.buttonPlayPause.setImageResource(drawable) }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun restorePlayerStatus() {
        binding.seekBar.isEnabled = playerAdapter?.isMediaPlayer() == true

        if (playerAdapter != null && playerAdapter?.isMediaPlayer() == true) {
            playerAdapter?.onResumeActivity()
            updatePlayingInfo(restore = true, startPlay = false)
        }
    }

    private fun doBindService() {
        bindService(
            Intent(
                this,
                MusicService::class.java
            ), connection, Context.BIND_AUTO_CREATE
        )
        isBound = true

        val startNotStickyIntent = Intent(this, MusicService::class.java)
        startService(startNotStickyIntent)
    }

    private fun doUnbindService() {
        if (isBound == true) {
            unbindService(connection)
            isBound = false
        }
    }

    private fun onSongClick(song: Song) {
        if (!binding.seekBar.isEnabled) {
            binding.seekBar.isEnabled = true
        }
        try {
            playerAdapter?.setCurrentSong(song)
            playerAdapter?.initMediaPlayer()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun skipPrevious() {
        if (checkIsPlayer()) {
            playerAdapter?.instantReset()
        }
    }

    private fun playOrPause() {
        if (checkIsPlayer()) {
            playerAdapter?.resumeOrPause()
        }
    }

    private fun skipNext() {
        if (checkIsPlayer()) {
            playerAdapter?.skip(true)
        }
    }

    private fun checkIsPlayer(): Boolean {
        return playerAdapter?.isMediaPlayer() == true
    }

    internal inner class PlaybackListener : PlaybackInfoListener() {

        override fun onPositionChanged(position: Int) {
            if (!userIsSeeking) {
                binding.seekBar.progress = position
            }
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun onStateChanged(@State state: Int) {

            updatePlayingStatus()
            if (playerAdapter?.getState() != State.PAUSED
                && playerAdapter?.getState() != State.PAUSED
            ) {
                updatePlayingInfo(restore = false, startPlay = true)
            }
        }
    }
}
