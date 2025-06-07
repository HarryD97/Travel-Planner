package com.example.travel.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.travel.data.DiaryWaypoint
import com.example.travel.databinding.ItemWaypointEditBinding

class WaypointEditAdapter(
    private val onDeleteClick: (DiaryWaypoint) -> Unit
) : ListAdapter<DiaryWaypoint, WaypointEditAdapter.WaypointViewHolder>(WaypointDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WaypointViewHolder {
        val binding = ItemWaypointEditBinding.inflate(
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
        private val binding: ItemWaypointEditBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(waypoint: DiaryWaypoint) {
            binding.apply {
                tvWaypointName.text = waypoint.location.name
                tvWaypointAddress.text = waypoint.location.address.ifEmpty { "Unknown Address" }
                tvWaypointSteps.text = "Steps: ${waypoint.stepsAtPoint}"

                btnDeleteWaypoint.setOnClickListener {
                    onDeleteClick(waypoint)
                }
            }
        }
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