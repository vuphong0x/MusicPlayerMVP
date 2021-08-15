package com.phongvv.musicplayermvp.utils

import android.content.ContentResolver
import com.phongvv.musicplayermvp.data.SongRepository
import java.util.*
import java.util.concurrent.TimeUnit

object Utils {
    const val NUM_ZERO: Int = 0
    const val NUM_ONE: Int = 1
    const val timeDelay: Long = 0
    const val timePeriod: Long = 1000
    const val currentPosition = 5000
    const val delayMillis: Long = 200

    fun formatDuration(duration: Int): String {
        return String.format(
            Locale.getDefault(), "%02d:%02d",
            TimeUnit.MILLISECONDS.toMinutes(duration.toLong()),
            TimeUnit.MILLISECONDS.toSeconds(duration.toLong()) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration.toLong()))
        )
    }

    fun getSongRepository(contentResolver: ContentResolver): SongRepository {
        val local = SongProvider.getInstance(contentResolver)
        return SongRepository.getInstance(local)
    }
}
