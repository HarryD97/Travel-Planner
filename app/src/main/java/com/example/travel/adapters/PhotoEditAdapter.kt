package com.example.travel.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.travel.data.DiaryPhoto
import com.example.travel.databinding.ItemPhotoEditBinding
import java.text.SimpleDateFormat
import java.util.*

class PhotoEditAdapter(
    private val onDeleteClick: (DiaryPhoto) -> Unit
) : ListAdapter<DiaryPhoto, PhotoEditAdapter.PhotoViewHolder>(PhotoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoEditBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PhotoViewHolder(
        private val binding: ItemPhotoEditBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: DiaryPhoto) {
            binding.apply {
                // Load photo
                Glide.with(itemView.context)
                    .load(Uri.parse(photo.filePath))
                    .centerCrop()
                    .into(ivPhoto)

                // Set time
                tvPhotoTime.text = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(photo.timestamp)

                // Set location information
                tvPhotoLocation.text = photo.location?.name ?: "No location information"

                btnDeletePhoto.setOnClickListener {
                    onDeleteClick(photo)
                }
            }
        }
    }

    private class PhotoDiffCallback : DiffUtil.ItemCallback<DiaryPhoto>() {
        override fun areItemsTheSame(oldItem: DiaryPhoto, newItem: DiaryPhoto): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DiaryPhoto, newItem: DiaryPhoto): Boolean {
            return oldItem == newItem
        }
    }
}