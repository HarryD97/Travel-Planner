package com.example.travel.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.travel.databinding.BottomSheetNavigationOptionsBinding
import com.example.travel.services.TravelMode
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class NavigationOptionsBottomSheet : BottomSheetDialogFragment() {
    
    private var _binding: BottomSheetNavigationOptionsBinding? = null
    private val binding get() = _binding!!
    
    private var destinationName: String = ""
    private var onNavigationSelected: ((TravelMode, Boolean) -> Unit)? = null
    
    companion object {
        fun newInstance(
            destinationName: String,
            onNavigationSelected: (TravelMode, Boolean) -> Unit
        ): NavigationOptionsBottomSheet {
            return NavigationOptionsBottomSheet().apply {
                this.destinationName = destinationName
                this.onNavigationSelected = onNavigationSelected
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetNavigationOptionsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupDestinationInfo()
        setupNavigationOptions()
    }
    
    private fun setupDestinationInfo() {
        binding.tvDestinationName.text = "Navigate to: $destinationName"
        binding.tvInstructions.text = "Select your preferred navigation method:"
    }
    
    private fun setupNavigationOptions() {
        // Internal navigation options
        binding.btnDriving.setOnClickListener {
            onNavigationSelected?.invoke(TravelMode.DRIVING, false)
            dismiss()
        }
        
        binding.btnWalking.setOnClickListener {
            onNavigationSelected?.invoke(TravelMode.WALKING, false)
            dismiss()
        }
        
        binding.btnCycling.setOnClickListener {
            onNavigationSelected?.invoke(TravelMode.BICYCLING, false)
            dismiss()
        }
        
        binding.btnTransit.setOnClickListener {
            onNavigationSelected?.invoke(TravelMode.TRANSIT, false)
            dismiss()
        }
        
        // External navigation
        binding.btnExternalNavigation.setOnClickListener {
            onNavigationSelected?.invoke(TravelMode.DRIVING, true)
            dismiss()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 