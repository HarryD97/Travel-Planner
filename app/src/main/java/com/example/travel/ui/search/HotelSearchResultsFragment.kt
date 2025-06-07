package com.example.travel.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.travel.adapters.HotelAdapter
import com.example.travel.data.Location
import com.example.travel.databinding.FragmentHotelSearchResultsBinding
import com.example.travel.services.Hotel
import com.example.travel.ui.itinerary.ItineraryViewModel

class HotelSearchResultsFragment : Fragment() {

    private var _binding: FragmentHotelSearchResultsBinding? = null
    private val binding get() = _binding!!

    private lateinit var hotelAdapter: HotelAdapter
    private lateinit var viewModel: SearchViewModel
    private lateinit var itineraryViewModel: ItineraryViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHotelSearchResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupRecyclerView()
        setupObservers()

        // Get search parameters and perform search - use default values for testing
        val latitude = arguments?.getString("latitude")?.toDoubleOrNull() ?: 39.9042
        val longitude = arguments?.getString("longitude")?.toDoubleOrNull() ?: 116.4074
        val locationName = arguments?.getString("locationName", "Beijing") ?: "Beijing"

        binding.tvSearchLocation.text = "Nearby Hotels: $locationName"

        // Perform search
        viewModel.searchHotels(latitude, longitude)
    }

    private fun setupUI() {
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        )[SearchViewModel::class.java]

        itineraryViewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
        )[ItineraryViewModel::class.java]

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Sort buttons
        binding.btnSortPrice.setOnClickListener {
            viewModel.sortHotelsByPrice()
        }

        binding.btnSortRating.setOnClickListener {
            viewModel.sortHotelsByRating()
        }

        binding.btnSortDistance.setOnClickListener {
            viewModel.sortHotelsByDistance()
        }
    }

    private fun setupRecyclerView() {
        hotelAdapter = HotelAdapter(
            onHotelClick = { hotel ->
                showHotelDetails(hotel)
            },
            onAddToItinerary = { hotel ->
                addHotelToItinerary(hotel)
            }
        )

        binding.recyclerViewHotels.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = hotelAdapter
        }
    }

    private fun setupObservers() {
        viewModel.hotels.observe(viewLifecycleOwner) { hotels ->
            hotelAdapter.submitList(hotels)
            binding.tvResultCount.text = "Found ${hotels.size} hotels"
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                viewModel.clearErrorMessage()
            }
        }
    }

    private fun showHotelDetails(hotel: Hotel) {
        // Create hotel details dialog
        val details = buildString {
            append("Hotel: ${hotel.name}\n")
            append("Address: ${hotel.address}\n")
            append("Rating: ${String.format("%.1f", hotel.rating)} Stars\n")
            append("Price: ${hotel.price}\n")
            if (hotel.distanceText.isNotEmpty()) {
                append("Distance: ${hotel.distanceText}\n")
            }
            if (hotel.amenities.isNotEmpty()) {
                append("Amenities: ${hotel.amenities.joinToString(", ")}\n")
            }
            if (hotel.description.isNotEmpty()) {
                append("\n${hotel.description}")
            }
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Hotel Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun addHotelToItinerary(hotel: Hotel) {
        // Convert hotel to Location and add to itinerary
        val location = Location(
            latitude = hotel.latitude,
            longitude = hotel.longitude,
            name = hotel.name,
            address = hotel.address
        )

        itineraryViewModel.addItineraryItem(location)

        Toast.makeText(
            context,
            "Added ${hotel.name} to itinerary",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}