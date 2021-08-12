package com.phongvv.musicplayermvp.utils

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.util.Log
import com.phongvv.musicplayermvp.data.LocalAsyncTask
import com.phongvv.musicplayermvp.data.OnDataLocalCallback
import com.phongvv.musicplayermvp.data.SongDataSource
import com.phongvv.musicplayermvp.models.Song

class SongProvider private constructor(
    private val contentResolver: ContentResolver
) : SongDataSource {

    private val BASE_PROJECTION = arrayOf(
        MediaStore.Audio.AudioColumns.TITLE,
        MediaStore.Audio.AudioColumns.DURATION,
        MediaStore.Audio.AudioColumns.DATA,
        MediaStore.Audio.AudioColumns.ALBUM,
        MediaStore.Audio.AudioColumns.ARTIST
    )

    private val allDeviceSongs = ArrayList<Song>()

    override fun getAllSong(callback: OnDataLocalCallback<List<Song>>) {
        LocalAsyncTask<Unit, List<Song>>(callback) {
            getAllDeviceSongs()
        }.execute(Unit)
    }

    private fun getAllDeviceSongs(): MutableList<Song> {
        val cursor = makeSongCursor()
        return getSongs(cursor)
    }

    private fun getSongs(cursor: Cursor?): MutableList<Song> {
        val songs = ArrayList<Song>()
        if (cursor != null && cursor.moveToFirst()) {
            do {
                val song = getSongFromCursorImpl(cursor)
                if (song.duration >= MAX_DURATION) {
                    songs.add(song)
                    allDeviceSongs.add(song)
                }
            } while (cursor.moveToNext())
        }
        cursor?.close()
        return songs
    }

    private fun getSongFromCursorImpl(cursor: Cursor): Song {
        val title = cursor.getString(TITLE)
        val duration = cursor.getInt(DURATION)
        val uri = cursor.getString(PATH)
        val albumName = cursor.getString(ALBUM)
        val artistName = cursor.getString(ARTIST)

        return Song(title, duration, uri, albumName, artistName)
    }

    @SuppressLint("Recycle")
    private fun makeSongCursor(): Cursor? {
        return try {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                BASE_PROJECTION, null, null, null
            )
        } catch (e: SecurityException) {
            null
        }
    }

    companion object {
        private const val TITLE = 0
        private const val DURATION = 1
        private const val PATH = 2
        private const val ALBUM = 3
        private const val ARTIST = 4
        private const val MAX_DURATION = 30000

        private var instance: SongProvider? = null
        fun getInstance(contentResolver: ContentResolver) =
            instance ?: SongProvider(contentResolver).also { instance = it }
    }
}
