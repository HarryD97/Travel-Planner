package com.example.travel.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.travel.data.TravelDiary
import com.example.travel.databinding.ItemDiaryBinding
import java.text.SimpleDateFormat
import java.util.*

class DiaryAdapter(
    private val onDiaryClick: (TravelDiary) -> Unit,
    private val onEditClick: (TravelDiary) -> Unit,
    private val onDeleteClick: (TravelDiary) -> Unit
) : ListAdapter<TravelDiary, DiaryAdapter.DiaryViewHolder>(DiaryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiaryViewHolder {
        val binding = ItemDiaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return DiaryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DiaryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DiaryViewHolder(
        private val binding: ItemDiaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(diary: TravelDiary) {
            binding.apply {
                tvDiaryTitle.text = diary.title
                tvDiaryDescription.text = diary.description
                tvDiaryDate.text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(diary.date)
                tvStepCount.text = "${diary.totalSteps} steps"
                tvWaypointCount.text = "${diary.waypoints.size} waypoints"
                tvPhotoCount.text = "${diary.photos.size} photos"
                tvDistance.text = if (diary.totalDistance > 0) "%.2f km".format(diary.totalDistance) else "0.00 km"
                
                val statusText = if (diary.isCompleted) "Completed" else "In Progress"
                tvStatus.text = statusText
                
                root.setOnClickListener {
                    onDiaryClick(diary)
                }
                
                btnEdit.setOnClickListener {
                    onEditClick(diary)
                }
                
                btnDelete.setOnClickListener {
                    onDeleteClick(diary)
                }
            }
        }
    }

    private class DiaryDiffCallback : DiffUtil.ItemCallback<TravelDiary>() {
        override fun areItemsTheSame(oldItem: TravelDiary, newItem: TravelDiary): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TravelDiary, newItem: TravelDiary): Boolean {
            return oldItem == newItem
        }
    }
} 