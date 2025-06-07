package com.example.travel.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.travel.R
import com.example.travel.databinding.ItemHotelBinding
import com.example.travel.services.Hotel

class HotelAdapter(
    private val onHotelClick: (Hotel) -> Unit,
    private val onAddToItinerary: (Hotel) -> Unit
) : ListAdapter<Hotel, HotelAdapter.HotelViewHolder>(HotelDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HotelViewHolder {
        val binding = ItemHotelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HotelViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HotelViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HotelViewHolder(
        private val binding: ItemHotelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(hotel: Hotel) {
            binding.apply {
                tvHotelName.text = hotel.name
                tvHotelAddress.text = hotel.address
                tvHotelRating.text = String.format("%.1f", hotel.rating)
                tvHotelPrice.text = hotel.price

                // Display distance information
                if (hotel.distanceText.isNotEmpty()) {
                    tvHotelDistance.text = hotel.distanceText
                } else {
                    tvHotelDistance.text = "--"
                }

                // Display amenity information
                if (hotel.amenities.isNotEmpty()) {
                    tvHotelAmenities.text = hotel.amenities.take(3).joinToString(" • ")
                } else {
                    tvHotelAmenities.text = "No amenity information available"
                }

                // Load hotel image (simulated)
                if (hotel.imageUrl.isNotEmpty()) {
                    Glide.with(itemView.context)
                        .load(hotel.imageUrl)
                        .placeholder(R.drawable.ic_hotel_24)
                        .error(R.drawable.ic_hotel_24)
                        .into(ivHotelImage)
                } else {
                    // Use default hotel icon
                    ivHotelImage.setImageResource(R.drawable.ic_hotel_24)
                }

                // Click event
                root.setOnClickListener {
                    onHotelClick(hotel)
                }

                // Add to itinerary button click event
                btnAddToItinerary.setOnClickListener {
                    onAddToItinerary(hotel)
                }
            }
        }
    }

    private class HotelDiffCallback : DiffUtil.ItemCallback<Hotel>() {
        override fun areItemsTheSame(oldItem: Hotel, newItem: Hotel): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Hotel, newItem: Hotel): Boolean {
            return oldItem == newItem
        }
    }
}