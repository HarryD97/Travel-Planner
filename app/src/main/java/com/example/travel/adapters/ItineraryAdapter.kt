package com.example.travel.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.travel.R
import com.example.travel.data.ItineraryItem
import com.example.travel.databinding.ItemItineraryBinding
import java.util.Collections

class ItineraryAdapter(
    private val onItemClick: (ItineraryItem) -> Unit,
    private val onDeleteClick: (ItineraryItem) -> Unit,
    private val onVisitedToggle: (ItineraryItem) -> Unit,
    private val onHotelSearchClick: (ItineraryItem) -> Unit,
    private val onItemMoved: (List<ItineraryItem>) -> Unit,
    private val onSelectionToggle: (ItineraryItem, Boolean) -> Unit
) : ListAdapter<ItineraryItem, ItineraryAdapter.ItineraryViewHolder>(ItineraryDiffCallback()), 
    ItemTouchHelperAdapter {

    private val items = mutableListOf<ItineraryItem>()
    private val selectedItems = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItineraryViewHolder {
        val binding = ItemItineraryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ItineraryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItineraryViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun submitList(list: List<ItineraryItem>?) {
        super.submitList(list)
        items.clear()
        list?.let { items.addAll(it) }
    }

    fun getSelectedItems(): List<ItineraryItem> {
        return items.filter { selectedItems.contains(it.id) }
    }

    fun clearSelection() {
        selectedItems.clear()
        notifyDataSetChanged()
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        Collections.swap(items, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
        
        // Update order numbers
        items.forEachIndexed { index, item ->
            items[index] = item.copy(order = index)
        }
        
        // Notify the callback
        onItemMoved(items.toList())
        return true
    }

    inner class ItineraryViewHolder(
        private val binding: ItemItineraryBinding
    ) : RecyclerView.ViewHolder(binding.root), ItemTouchHelperViewHolder {

        fun bind(item: ItineraryItem, position: Int) {
            binding.apply {
                tvLocationName.text = item.location.name
                tvLocationAddress.text = item.location.address
                tvOrder.text = (item.order + 1).toString()
                
                // Set selection state (repurpose the visited checkbox for selection)
                checkboxVisited.isChecked = selectedItems.contains(item.id)
                
                // Handle notes
                if (item.notes.isNotEmpty()) {
                    tvNotes.text = item.notes
                    tvNotes.visibility = android.view.View.VISIBLE
                } else {
                    tvNotes.visibility = android.view.View.GONE
                }
                
                // Handle photos count
                if (item.photos.isNotEmpty()) {
                    tvPhotosCount.text = "${item.photos.size} photos"
                    tvPhotosCount.visibility = android.view.View.VISIBLE
                } else {
                    tvPhotosCount.visibility = android.view.View.GONE
                }
                
                // Set time if available
                if (item.plannedTime.isNotEmpty()) {
                    tvPlannedTime.text = item.plannedTime
                    tvPlannedTime.visibility = android.view.View.VISIBLE
                } else {
                    tvPlannedTime.visibility = android.view.View.GONE
                }
                
                // Set click listeners
                root.setOnClickListener { onItemClick(item) }
                btnDelete.setOnClickListener { onDeleteClick(item) }
                btnSearchHotels.setOnClickListener { onHotelSearchClick(item) }
                
                // Handle selection toggle (repurpose visited checkbox)
                checkboxVisited.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedItems.add(item.id)
                    } else {
                        selectedItems.remove(item.id)
                    }
                    onSelectionToggle(item, isChecked)
                }
            }
        }

        override fun onItemSelected() {
            itemView.alpha = 0.7f
            itemView.elevation = 8f
        }

        override fun onItemClear() {
            itemView.alpha = 1.0f
            itemView.elevation = 0f
        }
    }

    private class ItineraryDiffCallback : DiffUtil.ItemCallback<ItineraryItem>() {
        override fun areItemsTheSame(oldItem: ItineraryItem, newItem: ItineraryItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ItineraryItem, newItem: ItineraryItem): Boolean {
            return oldItem == newItem
        }
    }
} 