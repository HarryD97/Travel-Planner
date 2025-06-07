package com.example.travel.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.travel.data.DiaryWaypoint
import com.example.travel.databinding.ItemWaypointDetailBinding
import java.text.SimpleDateFormat
import java.util.*

class DiaryDetailWaypointAdapter(
    private val onWaypointClick: (DiaryWaypoint) -> Unit
) : ListAdapter<DiaryWaypoint, DiaryDetailWaypointAdapter.WaypointViewHolder>(WaypointDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WaypointViewHolder {
        val binding = ItemWaypointDetailBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WaypointViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WaypointViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class WaypointViewHolder(
        private val binding: ItemWaypointDetailBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(waypoint: DiaryWaypoint) {
            binding.apply {
                tvWaypointName.text = waypoint.location.name
                tvWaypointAddress.text = waypoint.location.address.ifEmpty { 
                    "Coordinates: ${waypoint.location.latitude.format(4)}, ${waypoint.location.longitude.format(4)}"
                }
                tvWaypointTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(waypoint.timestamp)
                tvStepsAtPoint.text = "${waypoint.stepsAtPoint} steps"
                
                if (waypoint.notes.isNotEmpty()) {
                    tvWaypointNotes.text = waypoint.notes
                    tvWaypointNotes.visibility = android.view.View.VISIBLE
                } else {
                    tvWaypointNotes.visibility = android.view.View.GONE
                }
                
                // Add click listener for map navigation
                btnViewOnMap.setOnClickListener {
                    onWaypointClick(waypoint)
                }
                
                root.setOnClickListener {
                    onWaypointClick(waypoint)
                }
            }
        }
        
        private fun Double.format(digits: Int) = "%.${digits}f".format(this)
    }

    private class WaypointDiffCallback : DiffUtil.ItemCallback<DiaryWaypoint>() {
        override fun areItemsTheSame(oldItem: DiaryWaypoint, newItem: DiaryWaypoint): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DiaryWaypoint, newItem: DiaryWaypoint): Boolean {
            return oldItem == newItem
        }
    }
} 