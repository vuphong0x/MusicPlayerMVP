package com.phongvv.musicplayermvp.adapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.phongvv.musicplayermvp.databinding.ItemSongBinding
import com.phongvv.musicplayermvp.models.Song

class SongViewHolder(
    itemView: View,
    onItemClick: (Song) -> Unit,
) : RecyclerView.ViewHolder(itemView) {

    private val binding = ItemSongBinding.bind(itemView)
    private var itemSong: Song? = null

    init {
        binding.root.setOnClickListener {
            itemSong?.let { onItemClick(it) }
        }
    }

    fun bindData(item: Song) {
        itemSong = item
        binding.apply {
            textSongName.text = item.title
            textArtist.text = item.artistName
        }
    }
}
