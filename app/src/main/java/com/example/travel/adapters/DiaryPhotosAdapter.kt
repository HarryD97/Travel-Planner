package com.example.travel.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.travel.data.DiaryPhoto
import com.example.travel.databinding.ItemDiaryPhotoBinding
import java.text.SimpleDateFormat
import java.util.*

class DiaryPhotosAdapter(
    private val onPhotoClick: (String) -> Unit
) : ListAdapter<DiaryPhoto, DiaryPhotosAdapter.PhotoViewHolder>(PhotoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemDiaryPhotoBinding.inflate(
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
        private val binding: ItemDiaryPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: DiaryPhoto) {
            binding.apply {
                // Load image using Glide
                Glide.with(ivPhoto.context)
                    .load(photo.filePath)
                    .centerCrop()
                    .into(ivPhoto)
                
                tvPhotoTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(photo.timestamp)
                
                if (photo.caption.isNotEmpty()) {
                    tvPhotoCaption.text = photo.caption
                    tvPhotoCaption.visibility = android.view.View.VISIBLE
                } else {
                    tvPhotoCaption.visibility = android.view.View.GONE
                }
                
                photo.location?.let { location ->
                    tvPhotoLocation.text = location.name
                    tvPhotoLocation.visibility = android.view.View.VISIBLE
                } ?: run {
                    tvPhotoLocation.visibility = android.view.View.GONE
                }
                
                root.setOnClickListener {
                    onPhotoClick(photo.filePath)
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