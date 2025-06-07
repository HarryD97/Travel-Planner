package com.example.travel.ui.itinerary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.travel.R
import com.example.travel.adapters.ItineraryAdapter
import com.example.travel.adapters.ItemTouchHelperCallback
import com.example.travel.data.ItineraryItem
import com.example.travel.data.Location
import com.example.travel.databinding.FragmentItineraryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ItineraryFragment : Fragment() {

    private var _binding: FragmentItineraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var itineraryViewModel: ItineraryViewModel
    private lateinit var itineraryAdapter: ItineraryAdapter
    private val selectedLocations = mutableListOf<Location>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        itineraryViewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        )[ItineraryViewModel::class.java]
        _binding = FragmentItineraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupUI()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        itineraryAdapter = ItineraryAdapter(
            onItemClick = { item ->
                // Handle item click - show details
                showItemDetails(item)
            },
            onDeleteClick = { item ->
                itineraryViewModel.removeItineraryItem(item)
            },
            onVisitedToggle = { item ->
                // This callback is no longer used, but keeping for compatibility
            },
            onHotelSearchClick = { item ->
                // Search hotels near this specific location
                itineraryViewModel.searchHotels(
                    item.location.latitude,
                    item.location.longitude,
                    item.location.name
                )
            },
            onItemMoved = { newItems ->
                // Update the items with new order
                itineraryViewModel.updateItineraryOrder(newItems)
            },
            onSelectionToggle = { item, isSelected ->
                handleLocationSelection(item.location, isSelected)
            }
        )

        binding.recyclerViewItinerary.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = itineraryAdapter
        }

        // Setup drag and drop
        val callback = ItemTouchHelperCallback(itineraryAdapter)
        val touchHelper = ItemTouchHelper(callback)
        touchHelper.attachToRecyclerView(binding.recyclerViewItinerary)
    }

    private fun setupUI() {
        binding.fabAddLocation.setOnClickListener {
            // Navigate to map to add location
            findNavController().navigate(R.id.navigation_map)
        }

        binding.btnShowRoute.setOnClickListener {
            if (selectedLocations.size >= 2) {
                navigateToMapWithSelectedLocations()
            } else {
                Toast.makeText(context, "Please select at least 2 locations to display route", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClearAll.setOnClickListener {
            showClearAllConfirmationDialog()
        }
    }

    private fun handleLocationSelection(location: Location, isSelected: Boolean) {
        if (isSelected) {
            selectedLocations.add(location)
        } else {
            selectedLocations.remove(location)
        }

        // Update button visibility and text
        updateRouteButtonState()
    }

    private fun updateRouteButtonState() {
        val selectedCount = selectedLocations.size
        if (selectedCount >= 2) {
            binding.btnShowRoute.visibility = View.VISIBLE
            binding.btnShowRoute.text = "Show Route (${selectedCount} locations)"
        } else if (selectedCount == 1) {
            binding.btnShowRoute.visibility = View.VISIBLE
            binding.btnShowRoute.text = "At least 2 locations needed"
            binding.btnShowRoute.isEnabled = false
        } else {
            binding.btnShowRoute.visibility = View.GONE
        }

        // Re-enable button if we have enough locations
        if (selectedCount >= 2) {
            binding.btnShowRoute.isEnabled = true
        }
    }

    private fun navigateToMapWithSelectedLocations() {
        // Create a bundle with selected locations data
        val bundle = Bundle().apply {
            // Convert locations to JSON or use arguments
            val locationNames = selectedLocations.map { location -> location.name }.toTypedArray()
            val locationLatitudes = selectedLocations.map { location -> location.latitude }.toDoubleArray()
            val locationLongitudes = selectedLocations.map { location -> location.longitude }.toDoubleArray()
            val locationAddresses = selectedLocations.map { location -> location.address }.toTypedArray()

            putStringArray("locationNames", locationNames)
            putDoubleArray("locationLatitudes", locationLatitudes)
            putDoubleArray("locationLongitudes", locationLongitudes)
            putStringArray("locationAddresses", locationAddresses)
            putBoolean("showOptimizedRoute", true)
        }

        // Navigate to map fragment with the selected locations
        findNavController().navigate(R.id.navigation_map, bundle)

        // Clear selection after navigation
        clearSelection()

        Toast.makeText(context, "Navigating to map to display route", Toast.LENGTH_SHORT).show()
    }

    private fun clearSelection() {
        selectedLocations.clear()
        itineraryAdapter.clearSelection()
        updateRouteButtonState()
    }

    private fun showClearAllConfirmationDialog() {
        val itemCount = itineraryViewModel.itineraryItems.value?.size ?: 0
        if (itemCount == 0) return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear Itinerary")
            .setMessage("Are you sure you want to clear all itinerary items? This action cannot be undone.\n\nThere are currently $itemCount locations.")
            .setPositiveButton("Clear") { _, _ ->
                itineraryViewModel.clearAllItineraryItems()
                clearSelection() // Also clear any selection state
                Toast.makeText(context, "Itinerary cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun observeViewModel() {
        itineraryViewModel.itineraryItems.observe(viewLifecycleOwner) { items ->
            itineraryAdapter.submitList(items)
            updateEmptyState(items.isEmpty())
        }

        itineraryViewModel.travelStats.observe(viewLifecycleOwner) { stats ->
            binding.tvTotalDistance.text = "Distance: ${stats.totalDistance}"
            binding.tvTotalLocations.text = "Locations: ${stats.totalLocations}"
        }

        itineraryViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        itineraryViewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error.isNotEmpty()) {
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
        }

        // Observe navigation events
        itineraryViewModel.navigateToHotelSearch.observe(viewLifecycleOwner) { params ->
            params?.let {
                // Navigate to hotel search results page
                val bundle = Bundle().apply {
                    putString("latitude", it.latitude.toString())
                    putString("longitude", it.longitude.toString())
                    putString("locationName", it.locationName)
                }
                findNavController().navigate(R.id.hotel_search_results_fragment, bundle)
                itineraryViewModel.clearNavigationEvents()
            }
        }


    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyStateLayout.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.recyclerViewItinerary.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.btnClearAll.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showItemDetails(item: ItineraryItem) {
        // Show detailed view of itinerary item
        // This could be a dialog or bottom sheet
        Toast.makeText(context, "Show details for ${item.location.name}", Toast.LENGTH_SHORT).show()
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}