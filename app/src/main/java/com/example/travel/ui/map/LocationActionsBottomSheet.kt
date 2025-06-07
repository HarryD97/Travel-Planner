package com.example.travel.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.travel.R
import com.example.travel.data.Location
import com.example.travel.databinding.BottomSheetLocationActionsBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class LocationActionsBottomSheet : BottomSheetDialogFragment() {
    
    private var _binding: BottomSheetLocationActionsBinding? = null
    private val binding get() = _binding!!
    
    private var location: Location? = null
    private var onActionSelected: ((Action) -> Unit)? = null
    
    enum class Action {
        ADD_TO_ITINERARY,
        NAVIGATE_HERE,
        REMOVE_FROM_MAP,
        VIEW_DETAILS
    }
    
    companion object {
        fun newInstance(location: Location, onActionSelected: (Action) -> Unit): LocationActionsBottomSheet {
            return LocationActionsBottomSheet().apply {
                this.location = location
                this.onActionSelected = onActionSelected
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetLocationActionsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        location?.let { loc ->
            setupLocationInfo(loc)
            setupActions(loc)
        }
    }
    
    private fun setupLocationInfo(location: Location) {
        binding.tvLocationName.text = location.name
        binding.tvLocationAddress.text = location.address.ifEmpty { 
            "Coordinates: ${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}" 
        }
    }
    
    private fun setupActions(location: Location) {
        binding.btnAddToItinerary.setOnClickListener {
            onActionSelected?.invoke(Action.ADD_TO_ITINERARY)
            dismiss()
        }
        
        binding.btnNavigateHere.setOnClickListener {
            onActionSelected?.invoke(Action.NAVIGATE_HERE)
            dismiss()
        }
        
        binding.btnRemoveFromMap.setOnClickListener {
            onActionSelected?.invoke(Action.REMOVE_FROM_MAP)
            dismiss()
        }
        
        binding.btnViewDetails.setOnClickListener {
            onActionSelected?.invoke(Action.VIEW_DETAILS)
            dismiss()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 