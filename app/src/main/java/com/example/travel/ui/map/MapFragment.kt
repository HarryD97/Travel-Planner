package com.example.travel.ui.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.fragment.app.activityViewModels
import androidx.appcompat.app.AlertDialog
import com.example.travel.ui.itinerary.ItineraryViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.EditText
import android.widget.LinearLayout
import com.google.android.material.snackbar.Snackbar
import com.example.travel.R
import com.example.travel.data.Location
import com.example.travel.databinding.FragmentMapBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.example.travel.services.TravelMode
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    private lateinit var mapViewModel: MapViewModel
    private val itineraryViewModel: ItineraryViewModel by activityViewModels()
    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geocoder: Geocoder

    private var currentPolyline: Polyline? = null
    private var locationCallback: LocationCallback? = null
    private val markers = mutableListOf<com.google.android.gms.maps.model.Marker>()
    private var isNavigationModeEnabled = false
    private var isOptimizeRouteMode = false
    private val selectedLocationsForOptimization = mutableListOf<Location>()
    private var currentUserLocation: LatLng? = null
    private var currentTravelMode = TravelMode.DRIVING
    
    // Permission request launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableLocationServices()
        } else {
            Toast.makeText(context, "Location permission is required for current location features", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mapViewModel = ViewModelProvider(this)[MapViewModel::class.java]
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        geocoder = Geocoder(requireContext(), Locale.getDefault())

        // Initialize map
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        setupUI()
        observeViewModel()

        // Get initial location
        getCurrentLocation()
    }

    private fun setupUI() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchLocation(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })

        // First button: Return to current location
        binding.btnCurrentLocation.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                getCurrentLocation()
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // Second button: Clear all points on the map
        binding.btnClearMarkers.setOnClickListener {
            clearAllMarkersAndRoutes()
        }

        // Third button: Navigate in order of selected points
        binding.btnPlanRoute.setOnClickListener {
            if (isNavigationModeEnabled) {
                // In navigation mode, this button shows route information
                val route = mapViewModel.navigationRoute.value
                if (route != null) {
                    showRouteDetailsDialog(route)
                } else {
                    Toast.makeText(context, "No active route to display", Toast.LENGTH_SHORT).show()
                }
            } else {
                // If not in navigation mode, execute route planning in order
                planRouteInOrder()
            }
        }

        // Fourth button: Optimize driving route for all points on the map to make it shortest
        binding.btnOptimizeRoute.setOnClickListener {
            if (isOptimizeRouteMode) {
                // If already in optimization mode, execute optimization
                if (selectedLocationsForOptimization.size >= 2) {
                    optimizeSelectedRoute()
                } else {
                    Toast.makeText(context, "Please select at least 2 locations for route optimization", Toast.LENGTH_SHORT).show()
                }
            } else {
                // If not in optimization mode, switch to optimization mode
                toggleOptimizeRouteMode()
            }
        }

        // Fifth button: Only appears during navigation, start navigation
        binding.btnStartNavigation.setOnClickListener {
            mapViewModel.startNavigation()
        }

        // Travel mode button: Only shows during navigation to change travel mode
        binding.btnTravelMode.setOnClickListener {
            showTravelModeSelectionDialog()
        }
    }

    private fun observeViewModel() {
        mapViewModel.selectedLocations.observe(viewLifecycleOwner) { locations ->
            updateMapMarkers(locations)
        }

        mapViewModel.navigationRoute.observe(viewLifecycleOwner) { navigationRoute ->
            navigationRoute?.let {
                Log.d("MapFragment", "Received navigation route with ${it.waypoints.size} waypoints")
                drawRouteOnMap(it)
                binding.routeInfoCard.visibility = View.VISIBLE
                binding.btnStartNavigation.visibility = View.VISIBLE
                updateRouteInfo(it)
                Log.d("MapFragment", "Navigation route set, showing route info")

                // Show toast to confirm route display
                Toast.makeText(context, "Route displayed on map", Toast.LENGTH_SHORT).show()
            } ?: run {
                clearRouteFromMap()
                binding.routeInfoCard.visibility = View.GONE
                binding.btnStartNavigation.visibility = View.GONE
                Log.d("MapFragment", "Navigation route cleared, hiding route info")
            }
        }

        mapViewModel.currentNavigationStep.observe(viewLifecycleOwner) { step ->
            step?.let {
                showNavigationInstruction(it)
                Log.d("MapFragment", "Navigation step: ${it.instruction}")
            }
        }

        mapViewModel.isNavigating.observe(viewLifecycleOwner) { isNavigating ->
            isNavigationModeEnabled = isNavigating
            updateNavigationUI()
            if (isNavigating) {
                startLocationUpdates()
                // Show navigation mode selection button
                binding.btnTravelMode.visibility = View.VISIBLE
            } else {
                stopLocationUpdates()
                // Hide navigation mode selection button
                binding.btnTravelMode.visibility = View.GONE
                binding.navigationInstructionCard.visibility = View.GONE
            }
        }

        mapViewModel.currentLocation.observe(viewLifecycleOwner) { location ->
            location?.let {
                Log.d("MapFragment", "Current location updated: ${it.name}")
            }
        }

        mapViewModel.destinationLocation.observe(viewLifecycleOwner) { destination ->
            destination?.let {
                Log.d("MapFragment", "Destination set: ${it.name}")
            }
        }

        mapViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            // Show/hide loading indicator
            if (isLoading) {
                Log.d("MapFragment", "Loading navigation route...")
            } else {
                Log.d("MapFragment", "Route loading completed")
            }
        }

        mapViewModel.currentTravelMode.observe(viewLifecycleOwner) { travelMode ->
            if (isNavigationModeEnabled) {
                updateTravelModeIcon()
            }
            Log.d("MapFragment", "Travel mode changed to: ${travelMode.displayName}")
        }

        mapViewModel.isOptimizing.observe(viewLifecycleOwner) { isOptimizing ->
            if (isOptimizing) {
                Toast.makeText(context, getString(R.string.optimizing_route), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Handle waypoint data from arguments (from diary view)
        arguments?.let { args ->
            val latitude = args.getDouble("latitude", 0.0)
            val longitude = args.getDouble("longitude", 0.0)
            val locationName = args.getString("locationName", "")
            val showOptimizedRoute = args.getBoolean("showOptimizedRoute", false)

            if (latitude != 0.0 && longitude != 0.0) {
                val waypointLatLng = LatLng(latitude, longitude)

                // Create location object for the waypoint
                val waypointLocation = Location(
                    name = locationName,
                    address = "From Travel Diary",
                    latitude = latitude,
                    longitude = longitude
                )

                // Add marker for the waypoint
                val waypointMarker = googleMap.addMarker(
                    MarkerOptions()
                        .position(waypointLatLng)
                        .title(locationName)
                        .snippet("From Travel Diary")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                )

                // Set the location as tag so marker click works
                waypointMarker?.let {
                    it.tag = waypointLocation
                    markers.add(it)
                }

                // Move camera to waypoint
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(waypointLatLng, 16f)
                )

                Toast.makeText(context, "Navigated to: $locationName", Toast.LENGTH_SHORT).show()
            }

            // Handle selected locations from itinerary for route optimization
            if (showOptimizedRoute) {
                val locationNames = args.getStringArray("locationNames")
                val locationLatitudes = args.getDoubleArray("locationLatitudes")
                val locationLongitudes = args.getDoubleArray("locationLongitudes")
                val locationAddresses = args.getStringArray("locationAddresses")

                if (locationNames != null && locationLatitudes != null &&
                    locationLongitudes != null && locationAddresses != null &&
                    locationNames.size >= 2) {

                    val locations = mutableListOf<Location>()
                    for (i in locationNames.indices) {
                        locations.add(Location(
                            name = locationNames[i],
                            address = locationAddresses[i],
                            latitude = locationLatitudes[i],
                            longitude = locationLongitudes[i]
                        ))
                    }

                    // Add markers for all selected locations
                    locations.forEachIndexed { index, location ->
                        val marker = googleMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(location.latitude, location.longitude))
                                .title("${index + 1}. ${location.name}")
                                .snippet("From Itinerary Planning")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                        )
                        marker?.let {
                            it.tag = location
                            markers.add(it)
                        }
                    }

                    // Ensure map layout is complete and camera adjustment works
                    binding.root.post {
                        // First adjust camera to show all selected locations
                        adjustCameraToShowLocations(locations)

                        // Add a small delay before calculating route
                        binding.root.postDelayed({
                            // Then calculate and display optimized route
                            mapViewModel.calculateRoute(locations)
                        }, 300)
                    }

                    Toast.makeText(context, "Displayed optimized route for ${locations.size} locations", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Enable location if permission granted or request permission
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableLocationServices()
            if (arguments?.getDouble("latitude", 0.0) == 0.0) {
                getCurrentLocation() // Only get current location if not navigating to waypoint
            }
        } else {
            // Request permission using modern approach
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Set map click listener
        googleMap.setOnMapClickListener { latLng ->
            addLocationFromCoordinates(latLng)
        }

        // Remove long press listener, no longer support long press functionality
        googleMap.setOnMapLongClickListener(null)

        // Set marker click listener
        googleMap.setOnMarkerClickListener { marker ->
            Log.d("MapFragment", "Marker clicked: ${marker.title}")
            val location = marker.tag as? Location
            Log.d("MapFragment", "Marker tag: $location")
            if (location != null) {
                if (isOptimizeRouteMode) {
                    Log.d("MapFragment", "In optimize route mode")
                    toggleLocationSelection(location, marker)
                } else {
                    Log.d("MapFragment", "Showing location action dialog")
                    showLocationActionDialog(location)
                }
            } else {
                Log.w("MapFragment", "Marker has no location tag")
                Toast.makeText(context, "Marker tag is null", Toast.LENGTH_SHORT).show()
            }
            true
        }

        // Test polyline decoding with real data
        testPolylineDecoding()

        // Show hint about map functionality
        Snackbar.make(binding.root, "Tap map to add markers, tap markers to add to itinerary or navigate", Snackbar.LENGTH_LONG)
            .setAction("Learn") { showMapFeaturesGuide() }
            .show()
    }

    /**
     * Show location action dialog, support adding to itinerary and navigation functionality
     */
    private fun showLocationActionDialog(location: Location) {
        Log.d("MapFragment", "showLocationActionDialog called for: ${location.name}")

        val bottomSheet = LocationActionsBottomSheet.newInstance(location) { action ->
            when (action) {
                LocationActionsBottomSheet.Action.ADD_TO_ITINERARY -> {
                    Log.d("MapFragment", "Adding to itinerary selected")
                    addLocationToItinerary(location)
                }
                LocationActionsBottomSheet.Action.NAVIGATE_HERE -> {
                    Log.d("MapFragment", "Navigation selected")
                    navigateToLocationFromCurrent(location)
                }
                LocationActionsBottomSheet.Action.REMOVE_FROM_MAP -> {
                    Log.d("MapFragment", "Remove from map selected")
                    removeLocationFromMap(location)
                }
                LocationActionsBottomSheet.Action.VIEW_DETAILS -> {
                    Log.d("MapFragment", "View details selected")
                    showLocationDetails(location)
                }
            }
        }
        
        bottomSheet.show(childFragmentManager, "LocationActionsBottomSheet")
    }

    /**
     * Add location to itinerary
     */
    private fun addLocationToItinerary(location: Location) {
        itineraryViewModel.addItineraryItem(location)
        Toast.makeText(context, "Added \"${location.name}\" to itinerary", Toast.LENGTH_SHORT).show()
    }

    /**
     * Navigate from current location to specified location
     */
    private fun navigateToLocationFromCurrent(location: Location) {
        if (currentUserLocation == null) {
            Toast.makeText(context, "Cannot get current location, please enable location permission first", Toast.LENGTH_SHORT).show()
            getCurrentLocation()
            return
        }

        // Show navigation method selection dialog
        showNavigationOptionsDialog(LatLng(location.latitude, location.longitude), location.name)
    }



    /**
     * Show navigation options dialog
     */
    private fun showNavigationOptionsDialog(destination: LatLng, destinationName: String) {
        Log.d("MapFragment", "Showing navigation options dialog for: $destinationName")

        // Show bottom sheet for navigation options instead of AlertDialog
        val navigationBottomSheet = NavigationOptionsBottomSheet.newInstance(
            destinationName = destinationName,
            onNavigationSelected = { travelMode, isExternal ->
                if (isExternal) {
                    startExternalNavigation(destination, destinationName)
                } else {
                    startInternalNavigation(destination, destinationName, travelMode)
                }
            }
        )
        
        navigationBottomSheet.show(childFragmentManager, "NavigationOptionsBottomSheet")
    }

    /**
     * Start internal navigation
     */
    private fun startInternalNavigation(destination: LatLng, destinationName: String, mode: TravelMode) {
        val currentLocation = currentUserLocation ?: run {
            Toast.makeText(context, "Cannot get current location", Toast.LENGTH_SHORT).show()
            return
        }

        // Create start and end locations
        val startLocation = Location(
            name = "Current Location",
            address = "",
            latitude = currentLocation.latitude,
            longitude = currentLocation.longitude
        )

        val endLocation = Location(
            name = destinationName,
            address = "",
            latitude = destination.latitude,
            longitude = destination.longitude
        )

        // Set current travel mode
        currentTravelMode = mode
        updateTravelModeUI()

        // Clear existing markers and add new navigation route (without showing confirmation dialog)
        clearMarkersAndRoutesDirectly()

        // Add start and end markers
        val startMarker = googleMap.addMarker(
            MarkerOptions()
                .position(currentLocation)
                .title("Start Point")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )

        startMarker?.let {
            it.tag = startLocation
            markers.add(it)
        }

        val destinationMarker = googleMap.addMarker(
            MarkerOptions()
                .position(destination)
                .title("Destination: $destinationName")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
        )

        destinationMarker?.let {
            it.tag = endLocation
            markers.add(it)
        }

        // Set travel mode and calculate route
        mapViewModel.setTravelMode(mode)
        mapViewModel.calculateRoute(listOf(startLocation, endLocation))

        Toast.makeText(context, "Planning ${getTravelModeDescription(mode)} route to $destinationName", Toast.LENGTH_SHORT).show()
    }

    /**
     * Start external navigation application
     */
    private fun startExternalNavigation(destination: LatLng, destinationName: String) {
        try {
            // Build Google Maps navigation URI
            val uri = Uri.parse("google.navigation:q=${destination.latitude},${destination.longitude}&mode=d")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")

            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                startActivity(intent)
                Toast.makeText(context, "Opened Google Maps navigation to $destinationName", Toast.LENGTH_SHORT).show()
            } else {
                // If Google Maps is not installed, use generic map URI
                val genericUri = Uri.parse("geo:${destination.latitude},${destination.longitude}?q=${destination.latitude},${destination.longitude}($destinationName)")
                val genericIntent = Intent(Intent.ACTION_VIEW, genericUri)
                startActivity(genericIntent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not start external navigation application", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Add location marker on map
     */
    private fun addLocationMarker(latLng: LatLng, name: String, address: String) {
        val location = Location(
            name = name,
            address = address,
            latitude = latLng.latitude,
            longitude = latLng.longitude
        )

        val marker = googleMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(name)
                .snippet(address.ifEmpty { "Coordinates: ${String.format("%.4f", latLng.latitude)}, ${String.format("%.4f", latLng.longitude)}" })
        )

        marker?.let {
            it.tag = location
            markers.add(it)

            // If in optimization mode, update marker style
            if (isOptimizeRouteMode) {
                updateMarkersForOptimization()
            }
        }

        mapViewModel.addLocation(location)
        Toast.makeText(context, "Marked \"$name\" on map", Toast.LENGTH_SHORT).show()
    }

    /**
     * Plan route in order (third button functionality)
     */
    private fun planRouteInOrder() {
        val locations = mapViewModel.selectedLocations.value ?: emptyList()
        if (locations.size >= 2) {
            val routeBottomSheet = RouteManagementBottomSheet.newInstance(locations) { reorderedLocations ->
                mapViewModel.setTravelMode(currentTravelMode)
                mapViewModel.calculateRoute(reorderedLocations)
                Toast.makeText(context, "Planning ${getTravelModeDescription(currentTravelMode)} route with ${reorderedLocations.size} locations...", Toast.LENGTH_SHORT).show()
            }
            routeBottomSheet.show(childFragmentManager, "RouteManagementBottomSheet")
        } else {
            Toast.makeText(context, "Please mark at least 2 locations on the map to plan a route", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Clear all points and routes on the map (second button functionality)
     */
    private fun clearAllMarkersAndRoutes() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Clear Confirmation")
            .setMessage("Are you sure you want to clear all markers and routes from the map?")
            .setPositiveButton("Confirm") { _, _ ->
                clearMarkersAndRoutesDirectly()
                Toast.makeText(context, "All markers and routes cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Directly clear all points and routes on the map (without showing confirmation dialog)
     */
    private fun clearMarkersAndRoutesDirectly() {
        // Clear all markers from map
        markers.forEach { it.remove() }
        markers.clear()

        // Clear route polyline
        currentPolyline?.remove()
        currentPolyline = null

        // Clear data in ViewModel
        mapViewModel.clearAllData()

        // Reset modes
        selectedLocationsForOptimization.clear()
        isOptimizeRouteMode = false
        isNavigationModeEnabled = false
        updateUIForMode()

        // Hide UI elements
        binding.routeInfoCard.visibility = View.GONE
        binding.btnStartNavigation.visibility = View.GONE
        binding.navigationInstructionCard.visibility = View.GONE
        binding.btnTravelMode.visibility = View.GONE
    }

    /**
     * Show travel mode selection dialog during navigation (fifth button functionality)
     */


    /**
     * Show travel mode selection dialog
     */
    private fun showTravelModeSelectionDialog() {
        val modes = arrayOf("Driving", "Walking", "Cycling", "Transit")
        val modeValues = arrayOf(TravelMode.DRIVING, TravelMode.WALKING, TravelMode.BICYCLING, TravelMode.TRANSIT)

        val currentIndex = modeValues.indexOf(currentTravelMode)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Travel Mode")
            .setSingleChoiceItems(modes, currentIndex) { dialog, which ->
                currentTravelMode = modeValues[which]
                updateTravelModeUI()
                
                // If there's a current route, recalculate with new travel mode
                val locations = mapViewModel.selectedLocations.value
                if (!locations.isNullOrEmpty() && locations.size >= 2) {
                    mapViewModel.setTravelMode(currentTravelMode)
                    mapViewModel.calculateRoute(locations)
                    Toast.makeText(context, "Recalculating route for ${modes[which]} mode", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Selected ${modes[which]} travel mode", Toast.LENGTH_SHORT).show()
                }
                
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Update travel mode UI
     */
    private fun updateTravelModeUI() {
        val icon = when (currentTravelMode) {
            TravelMode.DRIVING -> R.drawable.ic_directions_car_24
            TravelMode.WALKING -> R.drawable.ic_directions_walk_24
            TravelMode.BICYCLING -> R.drawable.ic_directions_bike_24
            TravelMode.TRANSIT -> R.drawable.ic_directions_transit_24
        }
        binding.btnTravelMode.setImageResource(icon)
    }

    /**
     * Get travel mode description
     */
    private fun getTravelModeDescription(mode: TravelMode): String {
        return when (mode) {
            TravelMode.DRIVING -> "driving"
            TravelMode.WALKING -> "walking"
            TravelMode.BICYCLING -> "cycling"
            TravelMode.TRANSIT -> "transit"
        }
    }

    /**
     * Show route details dialog during navigation
     */
    private fun showRouteDetailsDialog(route: com.example.travel.services.NavigationRoute) {
        val stepsText = route.steps.take(5).joinToString("\n\n") { step ->
            "• ${step.instruction}\n  ${step.distance} • ${step.duration}"
        }
        
        val fullMessage = """
            🛣️ Current Route Information:
            
            📊 Route Summary:
            • Total Distance: ${route.totalDistance}
            • Estimated Duration: ${route.totalDuration}
            • Travel Mode: ${route.travelMode.displayName}
            
            📍 Next Steps:
            $stepsText
            ${if (route.steps.size > 5) "\n... and ${route.steps.size - 5} more steps" else ""}
        """.trimIndent()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Route Details")
            .setMessage(fullMessage)
            .setPositiveButton("Continue Navigation", null)
            .setNeutralButton("Stop Navigation") { _, _ ->
                mapViewModel.stopNavigation()
            }
            .show()
    }

    /**
     * Show map features guide
     */
    private fun showMapFeaturesGuide() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Map Features Guide")
            .setMessage("""
                🗺️ Map Operation Guide:
                
                📍 Basic Operations:
                • Tap Map - Add location marker
                • Tap Marker - Show action menu (add to itinerary, navigate)
                
                🔘 Function Buttons:
                • 📍 Locate - Return to current location
                • 🗑️ Clear - Clear all markers and routes
                • 🛣️ Route - Plan route in order (or view route details during navigation)
                • 🚏 Optimize - Optimize visit order
                • 🧭 Navigate - Start navigation (visible during navigation)
                
                🚗 Travel Modes:
                Supports four modes: driving, walking, cycling, transit
                Can use internal navigation or external navigation apps
            """.trimIndent())
            .setPositiveButton("Got it", null)
            .show()
    }

    /**
     * Update navigation UI state
     */
    private fun updateNavigationUI() {
        if (isNavigationModeEnabled) {
            binding.btnStartNavigation.visibility = View.VISIBLE
            binding.btnTravelMode.visibility = View.GONE  // Hide travel mode button during navigation
            binding.btnPlanRoute.contentDescription = "Route Preview"
            // Restore normal opacity since it's the only route-related button
            binding.btnPlanRoute.alpha = 1.0f
        } else {
            binding.btnTravelMode.visibility = View.GONE
            binding.btnPlanRoute.contentDescription = getString(R.string.route_planning)
            binding.btnPlanRoute.alpha = 1.0f
        }
        updateTravelModeIcon()
    }

    private fun updateTravelModeIcon() {
        if (isNavigationModeEnabled) {
            // In navigation mode, route button shows route details, use info icon
            binding.btnPlanRoute.setImageResource(R.drawable.ic_info_24)
        } else {
            // In non-navigation mode, show general route icon
            binding.btnPlanRoute.setImageResource(R.drawable.ic_directions_24)
        }
    }

    private fun testPolylineDecoding() {
        // Test with the actual polyline from the API response
        val testPolyline = "kdy`HznyhVkEcEO]G[Ai@De@Pg@\\_@NGTAVFXT`@p@x@`BRh@@P?P?LGJU\\Wf@[v@W|@G^eA}@e@_@q@i@aB_AOUWSyC{AkA]{@[e@OIASDoBq@uBs@k@QGYUKMEIFC?WKOGOC}@y@eA}Ao@}@WUs@]_@Oe@KsAQiBI}@?y@DqARaBl@iCvAu@d@g@`@gAtA_A`AkAt@?fE?fB@~B@lLuBBcA@i@Fm@LJj@Lr@FLFNBDCEGOGMMs@Kk@l@Mh@GbAAtBC@zH?zCBnL@`J?vE@`CKj@T@l@A|@?xB?lAAt@Bn@NrAd@r@H|EC~F@hAHjALbAP|@Vj@PbAZbBf@fDfApDbA`@Nl@JpAPRHFHNJZ`@Nf@Rh@ApCGbLInLIfH?|AJpCaAFg@DyBVgCZiAJwAFoABgCAeDI}BOaA?eAHg@LsC`AyCdA}A`@u@PuFbAuDt@_Cf@mCd@yB`@cCb@{Bh@aB`@eC|@wAl@aAh@eBlBoCzEyFzJ}A`D{A|DyC`IgDvHsDjI}HdQeBtDyC`HMXcAdC{AlDWr@Qn@s@lEg@nDEXC`EInVAdC@hAAz@Gr@UjA[~@g@r@"

        Log.d("MapFragment", "Testing polyline decoding...")
        val decodedPoints = decodePolyline(testPolyline)
        Log.d("MapFragment", "Test decode result: ${decodedPoints.size} points")

        if (decodedPoints.isNotEmpty()) {
            Log.d("MapFragment", "Test polyline bounds: ${decodedPoints.first()} to ${decodedPoints.last()}")

            // Expected coordinates based on the JSON data:
            // Southwest: 47.4836088, -122.2482802
            // Northeast: 47.5115363, -122.1923604
            val firstPoint = decodedPoints.first()
            val lastPoint = decodedPoints.last()

            Log.d("MapFragment", "Expected bounds: SW(47.4836088, -122.2482802) NE(47.5115363, -122.1923604)")
            Log.d("MapFragment", "Actual first: ${firstPoint}, last: ${lastPoint}")
        }
    }

    private fun toggleNavigationMode() {
        isNavigationModeEnabled = !isNavigationModeEnabled
        updateNavigationUI()

        val modeText = if (isNavigationModeEnabled)
            "Navigation mode enabled - Tap map for navigation"
        else
            "Navigation mode disabled"

        Snackbar.make(binding.root, modeText, Snackbar.LENGTH_SHORT).show()
    }

    private fun initializeNavigationModeUI() {
        // Set initial UI state without showing snackbar
        if (isNavigationModeEnabled) {
            updateTravelModeIcon()
            binding.btnPlanRoute.setColorFilter(ContextCompat.getColor(requireContext(), R.color.route_color))
        } else {
            binding.btnPlanRoute.setImageResource(R.drawable.ic_directions_24)
            binding.btnPlanRoute.clearColorFilter()
        }
    }

    private fun updateNavigationModeUI() {
        if (isNavigationModeEnabled) {
            // Navigation mode enabled - show navigation icon and change color
            updateTravelModeIcon()
            binding.btnPlanRoute.setColorFilter(ContextCompat.getColor(requireContext(), R.color.route_color))

            // Update hint text
            Snackbar.make(binding.root, "Navigation mode ON - Tap icon to change travel mode", Snackbar.LENGTH_SHORT).show()
        } else {
            // Navigation mode disabled - show route planning icon and reset color
            binding.btnPlanRoute.setImageResource(R.drawable.ic_directions_24)
            binding.btnPlanRoute.clearColorFilter()

            // Update hint text
            Snackbar.make(binding.root, getString(R.string.navigation_mode_hint_off), Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun updateMarkersForOptimization() {
        markers.forEach { marker ->
            val location = marker.tag as? Location
            if (location != null && isOptimizeRouteMode) {
                val isSelected = selectedLocationsForOptimization.contains(location)
                if (isSelected) {
                    marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                } else {
                    marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                }
            } else {
                marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            }
        }
    }

    private fun toggleLocationSelection(location: Location, marker: com.google.android.gms.maps.model.Marker) {
        if (selectedLocationsForOptimization.contains(location)) {
            selectedLocationsForOptimization.remove(location)
            marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            Toast.makeText(context, "Deselected: ${location.name}", Toast.LENGTH_SHORT).show()
        } else {
            selectedLocationsForOptimization.add(location)
            marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            Toast.makeText(context, "Selected: ${location.name} (${selectedLocationsForOptimization.size})", Toast.LENGTH_SHORT).show()
        }
    }

    private fun optimizeSelectedRoute() {
        if (selectedLocationsForOptimization.size < 2) {
            Toast.makeText(context, "Please select at least 2 locations", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Optimizing Route")
            .setMessage("Calculating optimal route, please wait...")
            .setCancelable(false)
            .create()
        progressDialog.show()

        mapViewModel.calculateRoute(selectedLocationsForOptimization.toList())

        mapViewModel.navigationRoute.observe(viewLifecycleOwner) { route ->
            if (route != null && isOptimizeRouteMode) {
                progressDialog.dismiss()
                showOptimizedRouteDialog(route)
            }
        }

        mapViewModel.isOptimizing.observe(viewLifecycleOwner) { isOptimizing ->
            if (!isOptimizing && progressDialog.isShowing) {
                progressDialog.dismiss()
            }
        }
    }

    private fun showOptimizedRouteDialog(route: com.example.travel.services.NavigationRoute) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Route Optimization Complete")
            .setMessage("Optimal route calculated:\n\n" +
                    "Total Distance: ${route.totalDistance}\n" +
                    "Estimated Time: ${route.totalDuration}\n" +
                    "Via ${selectedLocationsForOptimization.size} locations\n\n" +
                    "Do you want to add this route to your itinerary in order?")
            .setPositiveButton("Add to Itinerary") { _, _ ->
                addOptimizedRouteToItinerary(route)
            }
            .setNegativeButton("Show Route Only") { _, _ ->
                drawRouteOnMap(route)
            }
            .setNeutralButton("Cancel") { _, _ ->
            }
            .show()
    }

    private fun addOptimizedRouteToItinerary(route: com.example.travel.services.NavigationRoute) {
        val optimizedLocations = mapViewModel.selectedLocations.value ?: selectedLocationsForOptimization

        optimizedLocations.forEach { location ->
            itineraryViewModel.addItineraryItem(location)
        }

        drawRouteOnMap(route)

        isOptimizeRouteMode = false
        selectedLocationsForOptimization.clear()
        updateUIForMode()
        updateMarkersForOptimization()

        Toast.makeText(context,
            "Added ${optimizedLocations.size} locations to itinerary in optimal order",
            Toast.LENGTH_LONG).show()

        Snackbar.make(binding.root, "Route added to itinerary", Snackbar.LENGTH_LONG)
            .setAction("View Itinerary") {
            }
            .show()
    }

    private fun adjustCameraToShowLocations(locations: List<Location>) {
        if (locations.isEmpty()) {
            Log.w("MapFragment", "No locations to show on camera")
            return
        }

        Log.d("MapFragment", "Adjusting camera for ${locations.size} locations")

        try {
            if (locations.size == 1) {
                // Single location - zoom to a reasonable level
                val location = locations.first()
                val latLng = LatLng(location.latitude, location.longitude)

                Log.d("MapFragment", "Moving camera to single location: ${location.name} at ${location.latitude}, ${location.longitude}")

                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(latLng, 15f),
                    1500, // Animation duration in milliseconds
                    object : GoogleMap.CancelableCallback {
                        override fun onFinish() {
                            Log.d("MapFragment", "Camera animation finished for single location")
                        }

                        override fun onCancel() {
                            Log.w("MapFragment", "Camera animation cancelled for single location")
                        }
                    }
                )
            } else {
                // Multiple locations - show all with padding
                val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.Builder()
                locations.forEach { location ->
                    val latLng = LatLng(location.latitude, location.longitude)
                    boundsBuilder.include(latLng)
                    Log.d("MapFragment", "Added location to bounds: ${location.name} at ${location.latitude}, ${location.longitude}")
                }
                val bounds = boundsBuilder.build()

                Log.d("MapFragment", "Bounds calculated: ${bounds.southwest} to ${bounds.northeast}")

                // Add padding around the bounds (in pixels)
                val padding = 150

                try {
                    val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)

                    googleMap.animateCamera(
                        cameraUpdate,
                        2000, // Longer animation duration for multiple locations
                        object : GoogleMap.CancelableCallback {
                            override fun onFinish() {
                                Log.d("MapFragment", "Camera animation finished for ${locations.size} locations")
                            }

                            override fun onCancel() {
                                Log.w("MapFragment", "Camera animation cancelled for ${locations.size} locations")
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.w("MapFragment", "Failed to animate camera with bounds, trying moveCamera: ${e.message}")
                    // Fallback: try moveCamera instead of animateCamera
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                }
            }
        } catch (e: Exception) {
            Log.w("MapFragment", "Failed to adjust camera: ${e.message}")
            // Final fallback: just move to first location with immediate positioning
            if (locations.isNotEmpty()) {
                val firstLocation = locations.first()
                val latLng = LatLng(firstLocation.latitude, firstLocation.longitude)
                Log.d("MapFragment", "Fallback: moving to first location ${firstLocation.name}")
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 12f))
            }
        }
    }

    private fun clearRouteFromMap() {
        currentPolyline?.remove()
        currentPolyline = null
    }

    private fun parsePolylinePoints(polylineString: String): List<LatLng> {
        Log.d("MapFragment", "Parsing polyline string (length: ${polylineString.length})")
        Log.d("MapFragment", "Polyline sample: ${polylineString.take(50)}...")

        return try {
            val points = if (polylineString.contains("|")) {
                Log.d("MapFragment", "Using fallback pipe-separated format")
                // Fallback format from mock data
                polylineString.split("|").mapNotNull { point ->
                    val coords = point.split(",")
                    if (coords.size == 2) {
                        LatLng(coords[0].toDouble(), coords[1].toDouble())
                    } else null
                }
            } else {
                Log.d("MapFragment", "Using Google polyline encoding")
                // Google polyline encoding
                decodePolyline(polylineString)
            }

            Log.d("MapFragment", "Successfully parsed ${points.size} points")
            if (points.isNotEmpty()) {
                Log.d("MapFragment", "Sample points: ${points.take(3)}")
            }
            points
        } catch (e: Exception) {
            Log.e("MapFragment", "Error parsing polyline: ${e.message}", e)
            emptyList()
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        Log.d("MapFragment", "Decoding polyline of length: ${encoded.length}")
        val polylinePoints = mutableListOf<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0

        try {
            while (index < encoded.length) {
                // Decode latitude
                var shift = 0
                var result = 0
                var byte: Int
                do {
                    if (index >= encoded.length) break
                    byte = encoded[index++].code - 63
                    result = result or ((byte and 0x1f) shl shift)
                    shift += 5
                } while (byte >= 0x20 && index < encoded.length)

                val deltaLat = if ((result and 1) != 0) -(result shr 1) else (result shr 1)
                lat += deltaLat

                // Decode longitude
                shift = 0
                result = 0
                do {
                    if (index >= encoded.length) break
                    byte = encoded[index++].code - 63
                    result = result or ((byte and 0x1f) shl shift)
                    shift += 5
                } while (byte >= 0x20 && index < encoded.length)

                val deltaLng = if ((result and 1) != 0) -(result shr 1) else (result shr 1)
                lng += deltaLng

                val latLng = LatLng(lat / 1e5, lng / 1e5)
                polylinePoints.add(latLng)
            }

            Log.d("MapFragment", "Successfully decoded ${polylinePoints.size} points")
            if (polylinePoints.isNotEmpty()) {
                Log.d("MapFragment", "First decoded point: ${polylinePoints.first()}")
                Log.d("MapFragment", "Last decoded point: ${polylinePoints.last()}")
            }
        } catch (e: Exception) {
            Log.e("MapFragment", "Error decoding polyline: ${e.message}", e)
        }

        return polylinePoints
    }

    private fun updateRouteInfo(navigationRoute: com.example.travel.services.NavigationRoute) {
        binding.tvDistance.text = "Distance: ${navigationRoute.totalDistance}"
        binding.tvDuration.text = "Duration: ${navigationRoute.totalDuration}"
    }

    private fun showNavigationInstruction(step: com.example.travel.services.NavigationStep) {
        // Update navigation instruction card
        binding.navigationInstructionCard.visibility = View.VISIBLE
        binding.tvNavigationInstruction.text = step.instruction
        binding.tvStepDistance.text = "${step.distance} • ${step.duration}"

        // Update maneuver icon based on instruction type
        val iconRes = when (step.maneuver) {
            "TURN_LEFT" -> R.drawable.ic_turn_left_24
            "TURN_RIGHT" -> R.drawable.ic_turn_right_24
            "STRAIGHT" -> R.drawable.ic_straight_24
            "DEPART" -> R.drawable.ic_navigation_24
            "ARRIVE" -> R.drawable.ic_flag_24
            else -> R.drawable.ic_directions_24
        }
        binding.ivManeuverIcon.setImageResource(iconRes)
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val locationRequest = LocationRequest.create().apply {
                interval = 3000 // 3 seconds for navigation
                fastestInterval = 1000 // 1 second
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        // Update current location in ViewModel
                        mapViewModel.setCurrentLocation(location.latitude, location.longitude)
                        mapViewModel.updateCurrentLocation(location.latitude, location.longitude)

                        // Update camera to follow user location during navigation
                        val currentLatLng = LatLng(location.latitude, location.longitude)

                        // Use different zoom levels based on navigation state
                        val isNavigating = mapViewModel.isNavigating.value ?: false
                        val zoomLevel = if (isNavigating) 18f else 15f

                        googleMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(currentLatLng, zoomLevel)
                        )

                        Log.d("MapFragment", "Location updated: $currentLatLng")
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                null
            )

            Log.d("MapFragment", "Started location updates for navigation")
        }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f)
                    )

                    // Update current location in ViewModel
                    mapViewModel.setCurrentLocation(it.latitude, it.longitude)
                    currentUserLocation = currentLatLng
                    
                    Toast.makeText(context, "Current location found", Toast.LENGTH_SHORT).show()
                } ?: run {
                    // If last known location is null, try to get fresh location
                    requestFreshLocation()
                }
            }.addOnFailureListener {
                Toast.makeText(context, "Cannot get current location. Please check GPS settings.", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun requestFreshLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val locationRequest = LocationRequest.create().apply {
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                interval = 5000
                fastestInterval = 2000
                numUpdates = 1
            }
            
            val freshLocationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    locationResult.lastLocation?.let { location ->
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        googleMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f)
                        )
                        mapViewModel.setCurrentLocation(location.latitude, location.longitude)
                        currentUserLocation = currentLatLng
                        Toast.makeText(context, "Current location found", Toast.LENGTH_SHORT).show()
                    } ?: run {
                        Toast.makeText(context, "Unable to determine current location", Toast.LENGTH_LONG).show()
                    }
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }
            
            fusedLocationClient.requestLocationUpdates(locationRequest, freshLocationCallback, null)
        }
    }

    private fun addLocationFromCoordinates(latLng: LatLng) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                val address = addresses?.firstOrNull()

                val location = Location(
                    name = address?.featureName ?: "Unknown Location",
                    address = address?.getAddressLine(0) ?: "",
                    latitude = latLng.latitude,
                    longitude = latLng.longitude
                )

                withContext(Dispatchers.Main) {
                    addMarker(location)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val location = Location(
                        name = "Custom Location",
                        latitude = latLng.latitude,
                        longitude = latLng.longitude
                    )
                    addMarker(location)
                }
            }
        }
    }

    private fun addMarker(location: Location) {
        Log.d("MapFragment", "Adding marker for location: ${location.name} at ${location.latitude}, ${location.longitude}")
        val marker = googleMap.addMarker(
            MarkerOptions()
                .position(LatLng(location.latitude, location.longitude))
                .title(location.name)
                .snippet(location.address)
        )
        marker?.let {
            it.tag = location
            markers.add(it)
            Log.d("MapFragment", "Marker added with tag: ${it.tag}")

            // If in optimization mode, update marker style
            if (isOptimizeRouteMode) {
                updateMarkersForOptimization()
            }

            // If this is the second marker, show hint about optimization feature
            if (markers.size == 2 && !isOptimizeRouteMode) {
                showOptimizationHint()
            }
        }
        mapViewModel.addLocation(location)
    }

    // New: Show optimization route feature hint
    private fun showOptimizationHint() {
        Snackbar.make(binding.root, "Multiple locations added! Click the 🚏 button to use route optimization to plan the best visit order.", Snackbar.LENGTH_LONG)
            .setAction("Learn More") {
                showOptimizationGuide()
            }
            .show()
    }

    // New: Show optimization feature guide
    private fun showOptimizationGuide() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Route Optimization Feature")
            .setMessage("Route optimization helps you find the shortest path to visit all selected locations:\n\n" +
                    "1. Add multiple location markers on the map\n" +
                    "2. Click the 🚏 Optimize Route button to enter optimization mode\n" +
                    "3. Click markers to select locations to optimize (Green=selected)\n" +
                    "4. Click the Optimize button again to perform route calculation\n" +
                    "5. Choose to add the optimized route to your itinerary or just display it\n\n" +
                    "The system will use smart algorithms to calculate the best visit order for you, saving travel time and distance.")
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun updateMapMarkers(locations: List<Location>) {
        // Clear existing numbered markers
        clearNumberedMarkers()

        // Add numbered markers for the route
        locations.forEachIndexed { index, location ->
            addNumberedMarker(location, index + 1)
        }
    }

    private fun clearNumberedMarkers() {
        // Remove all markers except destination marker if in navigation mode
        markers.removeAll { marker ->
            if (isNavigationModeEnabled && marker.title == "Destination") {
                false // Keep destination marker
            } else {
                marker.remove()
                true
            }
        }
    }

    private fun addNumberedMarker(location: Location, number: Int) {
        val numberedIcon = createNumberedMarkerIcon(number)
        val marker = googleMap.addMarker(
            MarkerOptions()
                .position(LatLng(location.latitude, location.longitude))
                .title("${number}. ${location.name}")
                .snippet(location.address)
                .icon(BitmapDescriptorFactory.fromBitmap(numberedIcon))
        )
        marker?.let {
            it.tag = location
            markers.add(it)
        }
    }

    private fun createNumberedMarkerIcon(number: Int): Bitmap {
        val size = 100
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw circle background
        val paint = Paint().apply {
            color = ContextCompat.getColor(requireContext(), R.color.route_color)
            isAntiAlias = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, paint)

        // Draw white border
        paint.apply {
            color = ContextCompat.getColor(requireContext(), android.R.color.white)
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, paint)

        // Draw number text
        paint.apply {
            color = ContextCompat.getColor(requireContext(), android.R.color.white)
            style = Paint.Style.FILL
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        val textY = size / 2f - (paint.descent() + paint.ascent()) / 2
        canvas.drawText(number.toString(), size / 2f, textY, paint)

        return bitmap
    }





    private fun removeLocationFromMap(location: Location) {
        // Find and remove the marker for this location
        markers.removeIf { marker ->
            if (marker.tag == location) {
                marker.remove()
                true
            } else false
        }

        // Also remove from map view model
        mapViewModel.removeLocation(location)

        Toast.makeText(context, getString(R.string.location_removed_from_map, location.name), Toast.LENGTH_SHORT).show()
    }

    private fun planRoute() {
        val locations = mapViewModel.selectedLocations.value ?: return
        if (locations.size >= 2) {
            mapViewModel.calculateRoute(locations)
        } else {
            Toast.makeText(context, "Please select at least 2 locations", Toast.LENGTH_SHORT).show()
        }
    }

    private fun drawRouteOnMap(navigationRoute: com.example.travel.services.NavigationRoute) {
        // Clear existing route
        currentPolyline?.remove()

        // Parse polyline points and create LatLng list
        val points = parsePolylinePoints(navigationRoute.polylinePoints)

        Log.d("MapFragment", "Drawing route with ${points.size} points")
        if (points.isNotEmpty()) {
            Log.d("MapFragment", "First route point: ${points.first()}")
            Log.d("MapFragment", "Last route point: ${points.last()}")

            // Create polyline options for realistic car route
            val polylineOptions = PolylineOptions()
                .addAll(points)
                .color(ContextCompat.getColor(requireContext(), R.color.route_color))
                .width(16f) // Slightly thicker for better visibility
                .pattern(null)
                .geodesic(true) // Make the line follow Earth's curvature for more realistic appearance

            // Add polyline to map
            currentPolyline = googleMap.addPolyline(polylineOptions)
            Log.d("MapFragment", "Polyline added to map successfully")

            // Adjust camera to show the entire route with better animation
            adjustCameraToShowRoute(points)
            Log.d("MapFragment", "Camera adjusted to show route bounds")
        } else {
            Log.w("MapFragment", "No points to draw - polyline is empty")
        }
    }

    private fun adjustCameraToShowRoute(routePoints: List<LatLng>) {
        if (routePoints.isEmpty()) return

        try {
            val boundsBuilder = com.google.android.gms.maps.model.LatLngBounds.Builder()
            routePoints.forEach { boundsBuilder.include(it) }

            // Also include any existing markers
            markers.forEach { marker ->
                boundsBuilder.include(marker.position)
            }

            val bounds = boundsBuilder.build()
            val padding = 200 // More padding for route view

            // Use post to ensure smooth animation after route is drawn
            binding.root.post {
                try {
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(bounds, padding),
                        2000, // Slower animation for route
                        object : GoogleMap.CancelableCallback {
                            override fun onFinish() {
                                Log.d("MapFragment", "Route camera animation completed")
                            }

                            override fun onCancel() {
                                Log.d("MapFragment", "Route camera animation cancelled")
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.w("MapFragment", "Failed to animate camera for route: ${e.message}")
                    // Fallback to simple move
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                }
            }
        } catch (e: Exception) {
            Log.w("MapFragment", "Failed to adjust camera for route: ${e.message}")
        }
    }

    private fun toggleOptimizeRouteMode() {
        isOptimizeRouteMode = !isOptimizeRouteMode

        if (isOptimizeRouteMode) {
            selectedLocationsForOptimization.clear()
            updateMarkersForOptimization()

            // Show help message
            val message = if (markers.isEmpty()) {
                "Optimize route mode enabled. Please add some location markers on the map first, then click the markers to select route points to optimize."
            } else {
                "Optimize route mode enabled. Click markers on the map to select route points to optimize (Red=unselected, Green=selected). After selecting at least 2 points, click the optimize button again to perform optimization."
            }

            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                .setAction("Exit") {
                    isOptimizeRouteMode = false
                    selectedLocationsForOptimization.clear()
                    updateUIForMode()
                    updateMarkersForOptimization()
                }
                .show()
        } else {
            selectedLocationsForOptimization.clear()
            updateMarkersForOptimization()
            Toast.makeText(context, "Exited optimize route mode", Toast.LENGTH_SHORT).show()
        }

        updateUIForMode()
    }

    private fun updateUIForMode() {
        if (isOptimizeRouteMode) {
            // Optimize route mode: highlight optimize button, change icon color
            binding.btnOptimizeRoute.setImageResource(R.drawable.ic_check_24)
            binding.btnOptimizeRoute.contentDescription = "Complete Optimization"
            binding.btnOptimizeRoute.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.optimization_mode_color)
            
            // Show persistent banner for optimization mode
            binding.optimizationModeBanner.visibility = View.VISIBLE
            binding.optimizationModeBanner.text = "🎯 Optimization Mode: Tap locations to add to route"
        } else {
            // Normal mode: restore optimize button to normal state
            binding.btnOptimizeRoute.setImageResource(R.drawable.ic_route_24)
            binding.btnOptimizeRoute.contentDescription = "Optimize Route Mode"
            binding.btnOptimizeRoute.backgroundTintList = null
            binding.optimizationModeBanner.visibility = View.GONE

            // Update route planning button
            if (isNavigationModeEnabled) {
                updateTravelModeIcon()
            } else {
                binding.btnPlanRoute.setImageResource(R.drawable.ic_directions_24)
                binding.btnPlanRoute.contentDescription = getString(R.string.route_planning)
            }
        }
    }

    /**
     * Search location
     */
    private fun searchLocation(query: String) {
        if (query.isBlank()) {
            Toast.makeText(context, "Please enter a location to search", Toast.LENGTH_SHORT).show()
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val addresses = geocoder.getFromLocationName(query, 5) // Get up to 5 results
                withContext(Dispatchers.Main) {
                    when {
                        addresses?.isNotEmpty() == true -> {
                            if (addresses.size == 1) {
                                // Single result - add directly
                                val address = addresses[0]
                                val location = Location(
                                    name = query,
                                    address = address.getAddressLine(0) ?: "",
                                    latitude = address.latitude,
                                    longitude = address.longitude
                                )

                                val latLng = LatLng(address.latitude, address.longitude)
                                googleMap.animateCamera(
                                    CameraUpdateFactory.newLatLngZoom(latLng, 15f)
                                )
                                addLocationMarker(latLng, query, address.getAddressLine(0) ?: "")
                                Toast.makeText(context, "Found: ${address.getAddressLine(0)}", Toast.LENGTH_SHORT).show()
                            } else {
                                // Multiple results - show selection dialog
                                showSearchResultsDialog(query, addresses)
                            }
                        }
                        else -> {
                            Toast.makeText(context, "No results found for '$query'. Try:\n• Different spelling\n• City name\n• Landmark name", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Search failed. Please check your internet connection.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun showSearchResultsDialog(query: String, addresses: List<android.location.Address>) {
        val options = addresses.map { address ->
            address.getAddressLine(0) ?: "${address.locality ?: ""}, ${address.countryName ?: ""}"
        }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select Location")
            .setItems(options) { _, which ->
                val selectedAddress = addresses[which]
                val location = Location(
                    name = query,
                    address = selectedAddress.getAddressLine(0) ?: "",
                    latitude = selectedAddress.latitude,
                    longitude = selectedAddress.longitude
                )

                val latLng = LatLng(selectedAddress.latitude, selectedAddress.longitude)
                googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(latLng, 15f)
                )
                addLocationMarker(latLng, query, selectedAddress.getAddressLine(0) ?: "")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun enableLocationServices() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap.isMyLocationEnabled = true
        }
    }
    
    private fun showLocationDetails(location: Location) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Location Details")
            .setMessage(buildString {
                append("Name: ${location.name}\n")
                append("Address: ${location.address.ifEmpty { "Not available" }}\n")
                append("Coordinates: ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}\n")
                append("\nTap 'Navigate' to get directions to this location.")
            })
            .setPositiveButton("Navigate") { _, _ ->
                navigateToLocationFromCurrent(location)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopLocationUpdates()
        _binding = null
    }
}