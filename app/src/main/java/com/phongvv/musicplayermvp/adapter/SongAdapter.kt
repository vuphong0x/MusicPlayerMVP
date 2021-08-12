package com.phongvv.musicplayermvp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.phongvv.musicplayermvp.R
import com.phongvv.musicplayermvp.models.Song

class SongAdapter(
    private var onItemClick: (Song) -> Unit,
) : RecyclerView.Adapter<SongViewHolder>() {

    private val songs = mutableListOf<Song>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        SongViewHolder(
            LayoutInflater
                .from(parent.context)
                .inflate(R.layout.item_song, parent, false),
            onItemClick
        )

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bindData(songs[position])
    }

    override fun getItemCount() = songs.size

    fun updateData(songsList: List<Song>) {
        songs.apply {
            clear()
            addAll(songsList)
        }
        notifyDataSetChanged()
    }
}
