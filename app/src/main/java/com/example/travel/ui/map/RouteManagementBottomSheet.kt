package com.example.travel.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.travel.adapters.RouteLocationAdapter
import com.example.travel.adapters.RouteLocationItemTouchHelper
import com.example.travel.data.Location
import com.example.travel.databinding.BottomSheetRouteManagementBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class RouteManagementBottomSheet : BottomSheetDialogFragment() {
    
    private var _binding: BottomSheetRouteManagementBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var routeLocationAdapter: RouteLocationAdapter
    private var locations: MutableList<Location> = mutableListOf()
    private var onRouteConfirmed: ((List<Location>) -> Unit)? = null
    
    companion object {
        fun newInstance(
            locations: List<Location>, 
            onRouteConfirmed: (List<Location>) -> Unit
        ): RouteManagementBottomSheet {
            return RouteManagementBottomSheet().apply {
                this.locations = locations.toMutableList()
                this.onRouteConfirmed = onRouteConfirmed
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetRouteManagementBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupButtons()
        updateLocationCount()
    }
    
    private fun setupRecyclerView() {
        routeLocationAdapter = RouteLocationAdapter(
            locations = locations,
            onLocationRemoved = { position ->
                if (position in 0 until locations.size) {
                    locations.removeAt(position)
                    routeLocationAdapter.notifyItemRemoved(position)
                    updateLocationCount()
                }
            }
        )
        
        binding.recyclerViewRouteLocations.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = routeLocationAdapter
        }
        
        // Setup drag and drop
        val itemTouchHelper = ItemTouchHelper(
            RouteLocationItemTouchHelper(
                onItemMoved = { fromPosition, toPosition ->
                    val movedLocation = locations.removeAt(fromPosition)
                    locations.add(toPosition, movedLocation)
                    routeLocationAdapter.notifyItemMoved(fromPosition, toPosition)
                }
            )
        )
        
        itemTouchHelper.attachToRecyclerView(binding.recyclerViewRouteLocations)
    }
    
    private fun setupButtons() {
        binding.btnConfirmRoute.setOnClickListener {
            if (locations.size >= 2) {
                onRouteConfirmed?.invoke(locations.toList())
                dismiss()
            }
        }
        
        binding.btnCancel.setOnClickListener {
            dismiss()
        }
        
        binding.btnClearAll.setOnClickListener {
            locations.clear()
            routeLocationAdapter.notifyDataSetChanged()
            updateLocationCount()
        }
    }
    
    private fun updateLocationCount() {
        binding.tvLocationCount.text = "Route with ${locations.size} locations"
        binding.btnConfirmRoute.isEnabled = locations.size >= 2
        binding.tvInstructions.visibility = if (locations.isEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 