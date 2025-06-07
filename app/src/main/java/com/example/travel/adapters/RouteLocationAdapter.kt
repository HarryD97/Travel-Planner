package com.example.travel.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.travel.R
import com.example.travel.data.Location

class RouteLocationAdapter(
    private val locations: MutableList<Location>,
    private val onLocationRemoved: (Int) -> Unit
) : RecyclerView.Adapter<RouteLocationAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLocationName: TextView = view.findViewById(R.id.tvLocationName)
        val tvLocationAddress: TextView = view.findViewById(R.id.tvLocationAddress)
        val tvOrderNumber: TextView = view.findViewById(R.id.tvOrderNumber)
        val ivDragHandle: ImageView = view.findViewById(R.id.ivDragHandle)
        val ivRemove: ImageView = view.findViewById(R.id.ivRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_route_location, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val location = locations[position]
        
        holder.tvOrderNumber.text = "${position + 1}"
        holder.tvLocationName.text = location.name
        holder.tvLocationAddress.text = location.address.ifEmpty { 
            "Coordinates: ${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}" 
        }
        
        holder.ivRemove.setOnClickListener {
            onLocationRemoved(holder.adapterPosition)
        }
    }

    override fun getItemCount() = locations.size
} 