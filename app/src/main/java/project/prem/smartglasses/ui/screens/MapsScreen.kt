package project.prem.smartglasses.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import project.prem.smartglasses.utils.LocationManager
import project.prem.smartglasses.utils.OSMMapHelper
import project.prem.smartglasses.utils.NavigationManager
import project.prem.smartglasses.utils.SpeechRecognizer
import project.prem.smartglasses.utils.TextToSpeech
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import project.prem.smartglasses.ml.ObjectDetectionModel
import project.prem.smartglasses.utils.CameraManager

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*


@Composable
fun MapsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Initialize osmdroid configuration
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, context.getSharedPreferences("osm_prefs", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "Navigator"
    }

    // Initialize managers
    val locationManager = remember { LocationManager(context) }
    val navigationManager = remember { NavigationManager(context) }
    val speechRecognizer = remember { SpeechRecognizer(context) }
    val textToSpeech = remember { TextToSpeech(context) }
    val osmMapHelper = remember { OSMMapHelper(context) }
    val objectDetectionModel = remember { ObjectDetectionModel(context) }
    val cameraManager = remember { CameraManager(context) }


    // State variables
    var searchQuery by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf(listOf<String>()) }
    var currentDirection by remember { mutableStateOf<String?>(null) }
    var isNavigating by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedDestination by remember { mutableStateOf("") }
    var isMapInitialized by remember { mutableStateOf(false) }
    var isProcessingFrame by remember { mutableStateOf(false) }
    var userLocation by remember { mutableStateOf<LatLng?>(null) }



    // Map state
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var routeOverlay by remember { mutableStateOf<Polyline?>(null) }
    var destinationMarker by remember { mutableStateOf<Marker?>(null) }
    var myLocationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }

    // Current location state
    val currentLocation = locationManager.currentLocation.collectAsState().value

    // Effect to update search results when query changes
    // Function to start navigation to the first result directly
    val startNavigationToFirstResult = { query: String ->
        scope.launch {
            try {
                isLoading = true
                errorMessage = null

                // Get the first place
                val firstPlace = navigationManager.findFirstPlace(query, currentLocation)

                if (firstPlace != null) {
                    val (lat, lon, displayName) = firstPlace

                    // Set the selected destination
                    selectedDestination = displayName

                    // Get directions
                    val direction = navigationManager.getDirections(
                        locationManager.getCurrentLocation(),
                        displayName
                    )

                    // Update UI with directions
                    currentDirection = direction
                    isNavigating = true

                    // Speak the directions
                    textToSpeech.speak(direction)
                } else {
                    errorMessage = "No results found for '$query'. Try a different search term."
                }
            } catch (e: Exception) {
                errorMessage = "Navigation error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Effect to auto-start navigation when query changes
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2 && !isNavigating && !isLoading) {
            // Small delay before starting navigation to allow user to see what they typed
            kotlinx.coroutines.delay(800)
            startNavigationToFirstResult(searchQuery)
        }
    }



    // Function to advance to next navigation instruction
    val getNextNavInstruction = {
        if (isNavigating) {
            navigationManager.getNextNavInstruction()?.let { instruction ->
                currentDirection = instruction
                textToSpeech.speak(instruction)

                // If this is the final instruction, end navigation after a delay
                if (instruction.contains("arrived")) {
                    isNavigating = false
                }
            }
        }
    }

    // Set up the main layout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Back button and search box row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                    contentDescription = "Back"
                )
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { newQuery ->
                    if (!isNavigating) {
                        searchQuery = newQuery
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp),
                placeholder = { Text("Search location...") },
                singleLine = true,
                enabled = !isNavigating,
                trailingIcon = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            )
        }

        // Show error message if any
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }


// Predictions list - only show when not navigating
        // Map view - takes most of the screen space
        // Map view
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // OpenStreetMap implementation
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        // Fix map in place - disable scrolling when navigating
                        isClickable = true


                        // Set initial location - using a location in India as default
                        val initialLocation = GeoPoint(28.6139, 77.2090) // New Delhi
                        controller.setCenter(initialLocation)

                        // Add my location overlay
                        myLocationOverlay = MyLocationNewOverlay(this).apply {
                            enableMyLocation()
                            enableFollowLocation()
                            setDrawAccuracyEnabled(true)
                            overlays.add(this)
                        }

                        mapView = this
                        isMapInitialized = true
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
                update = { mapInstance ->
                    // Only update if map is initialized
                    if (isMapInitialized) {
                        // Update map with current location
                        currentLocation?.let { location ->
                            val geoPoint = GeoPoint(location.latitude, location.longitude)

                            if (!isNavigating) {
                                // Auto-center map on current location when not navigating
                                mapInstance.controller.animateTo(geoPoint)
                            }

                            // Update my location overlay
                            myLocationOverlay?.let { overlay ->
                                if (!overlay.isMyLocationEnabled) {
                                    overlay.enableMyLocation()
                                }
                            }

                            // Draw route if navigating
                            if (isNavigating) {
                                // Get destination from navigation manager
                                val destination = navigationManager.getCurrentDestination()

                                // If we have a destination and no current route overlay
                                if (destination != null && routeOverlay == null) {
                                    // Remove any existing destination marker
                                    destinationMarker?.let { marker ->
                                        mapInstance.overlays.remove(marker)
                                    }

                                    // Add new destination marker
                                    val destPoint = GeoPoint(destination.first, destination.second)
                                    val marker = Marker(mapInstance).apply {
                                        position = destPoint
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        title = "Destination"
                                        snippet = selectedDestination
                                    }

                                    mapInstance.overlays.add(marker)
                                    destinationMarker = marker

                                    // Get route overlay from OSMMapHelper
                                    val road = osmMapHelper.getCurrentRoad()
                                    if (road != null) {
                                        val polyline = RoadManager.buildRoadOverlay(road)
                                        polyline.outlinePaint.color = android.graphics.Color.parseColor("#8B5CF6")
                                        polyline.outlinePaint.strokeWidth = 10f

                                        // Remove existing route overlay if any
                                        if (routeOverlay != null) {
                                            mapInstance.overlays.remove(routeOverlay)
                                        }

                                        // Add new route overlay
                                        mapInstance.overlays.add(polyline)
                                        routeOverlay = polyline

                                        // Fit map to show entire route with padding
                                        val bounds = osmMapHelper.getRouteBounds()
                                        if (bounds != null) {
                                            mapInstance.zoomToBoundingBox(bounds, true, 100)
                                        }
                                    }
                                }
                            } else {
                                // Clear route overlay and destination marker when not navigating
                                if (routeOverlay != null) {
                                    mapInstance.overlays.remove(routeOverlay)
                                    routeOverlay = null
                                }

                                if (destinationMarker != null) {
                                    mapInstance.overlays.remove(destinationMarker)
                                    destinationMarker = null
                                }
                            }
                        }

                        // Ensure my location overlay is always on top
                        myLocationOverlay?.let { overlay ->
                            mapInstance.overlays.remove(overlay)
                            mapInstance.overlays.add(overlay)
                        }

                        // Invalidate to redraw
                        mapInstance.invalidate()
                    }
                }
            )

            // Show current direction on top of map when navigating
            if (isNavigating && currentDirection != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // Destination
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Navigating to:",
                                style = MaterialTheme.typography.titleSmall
                            )
                            IconButton(
                                onClick = {
                                    isNavigating = false
                                    currentDirection = null
                                    // Clear route on map
                                    routeOverlay = null
                                    destinationMarker = null
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.Default.Close,
                                    contentDescription = "Close navigation",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Text(
                            text = selectedDestination,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Current instruction
                        Text(
                            text = "Current Direction",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = currentDirection ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        // Navigation buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Button(
                                onClick = getNextNavInstruction,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Next Direction")
                            }
                        }
                    }
                }
            }

            // Loading indicator
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Voice search button
        // Voice search button - only show when not navigating
        if (!isNavigating) {
            Button(
                onClick = {
                    speechRecognizer.startListening { spokenText ->
                        searchQuery = spokenText
                        // Auto-start navigation with voice input
                        startNavigationToFirstResult(spokenText)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                enabled = !isLoading
            ) {
                Text("Speak Destination")
            }
        }
    }

    // Handle lifecycle events for MapView
    DisposableEffect(Unit) {
        onDispose {
            mapView?.onDetach()
            myLocationOverlay?.disableMyLocation()
            locationManager.stopLocationUpdates()
        }
    }
}
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.material.icons.automirrored.filled.ArrowBack
//import androidx.compose.material.icons.filled.ArrowBack
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.text.style.TextOverflow
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.viewinterop.AndroidView
//import project.prem.smartglasses.utils.LocationManager
//import project.prem.smartglasses.utils.MapViewHelper
//import project.prem.smartglasses.utils.NavigationManager
//import project.prem.smartglasses.utils.SpeechRecognizer
//import project.prem.smartglasses.utils.TextToSpeech
//import com.google.android.gms.maps.CameraUpdateFactory
//import com.google.android.gms.maps.GoogleMap
//import com.google.android.gms.maps.MapView
//import com.google.android.gms.maps.model.LatLng
//import com.google.android.gms.maps.model.MarkerOptions
//import com.google.android.gms.maps.model.PolylineOptions
//import kotlinx.coroutines.flow.collectLatest
//import kotlinx.coroutines.launch
//
//@Composable
//fun MapsScreen(
//    onNavigateBack: () -> Unit
//) {
//    val context = LocalContext.current
//    val scope = rememberCoroutineScope()
//
//    // Initialize managers
//    val locationManager = remember { LocationManager(context) }
//    val navigationManager = remember { NavigationManager(context) }
//    val speechRecognizer = remember { SpeechRecognizer(context) }
//    val textToSpeech = remember { TextToSpeech(context) }
//    val mapViewHelper = remember { MapViewHelper(context) }
//
//    // State variables
//    var searchQuery by remember { mutableStateOf("") }
//    var predictions by remember { mutableStateOf(listOf<String>()) }
//    var currentDirection by remember { mutableStateOf<String?>(null) }
//    var isNavigating by remember { mutableStateOf(false) }
//    var isLoading by remember { mutableStateOf(false) }
//    var errorMessage by remember { mutableStateOf<String?>(null) }
//
//    // Map state
//    var mapView by remember { mutableStateOf<MapView?>(null) }
//    var googleMap by remember { mutableStateOf<GoogleMap?>(null) }
//
//    // Current location state
//    val currentLocation = locationManager.currentLocation.collectAsState().value
//
//    // Effect to update search results when query changes
//    LaunchedEffect(searchQuery) {
//        if (searchQuery.length >= 2) {
//            isLoading = true
//            try {
//                predictions = navigationManager.findPlace(searchQuery, currentLocation)
//                errorMessage = if (predictions.isEmpty()) "No places found" else null
//            } catch (e: Exception) {
//                errorMessage = "Error searching: ${e.message}"
//                predictions = emptyList()
//            } finally {
//                isLoading = false
//            }
//        } else {
//            predictions = emptyList()
//        }
//    }
//
//    // Set up the main layout
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp)
//    ) {
//        // Back button and search box row
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(bottom = 8.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            IconButton(onClick = onNavigateBack) {
//                Icon(
//                    imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
//                    contentDescription = "Back"
//                )
//            }
//
//            OutlinedTextField(
//                value = searchQuery,
//                onValueChange = { newQuery ->
//                    searchQuery = newQuery
//                },
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(start = 8.dp),
//                placeholder = { Text("Search location...") },
//                singleLine = true,
//                trailingIcon = {
//                    if (isLoading) {
//                        CircularProgressIndicator(
//                            modifier = Modifier.size(24.dp),
//                            strokeWidth = 2.dp
//                        )
//                    }
//                }
//            )
//        }
//
//        // Show error message if any
//        errorMessage?.let {
//            Text(
//                text = it,
//                color = MaterialTheme.colorScheme.error,
//                modifier = Modifier.padding(vertical = 4.dp)
//            )
//        }
//
//        // Predictions list
//        if (predictions.isNotEmpty()) {
//            LazyColumn(
//                modifier = Modifier
//                    .weight(0.3f)
//                    .fillMaxWidth()
//                    .background(MaterialTheme.colorScheme.surface)
//            ) {
//                items(predictions) { prediction ->
//                    Text(
//                        text = prediction,
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .clickable {
//                                searchQuery = prediction
//                                predictions = emptyList()
//                                scope.launch {
//                                    try {
//                                        isLoading = true
//
//                                        // Get directions
//                                        val direction = navigationManager.getDirections(
//                                            locationManager.getCurrentLocation(),
//                                            prediction
//                                        )
//
//                                        // Get route polyline for map
//                                        val routePolyline = mapViewHelper.getRoutePolyline(
//                                            locationManager.getCurrentLocation(),
//                                            prediction
//                                        )
//
//                                        // Update UI with directions
//                                        currentDirection = direction
//                                        isNavigating = true
//
//                                        // Update map with the route
//                                        googleMap?.let { map ->
//                                            map.clear()
//
//                                            // Add start marker (current location)
//                                            currentLocation?.let { location ->
//                                                val startLatLng = LatLng(location.latitude, location.longitude)
//                                                map.addMarker(MarkerOptions().position(startLatLng).title("Current location"))
//                                            }
//
//                                            // Add polyline for the route
//                                            if (routePolyline.isNotEmpty()) {
//                                                map.addPolyline(
//                                                    PolylineOptions()
//                                                        .addAll(routePolyline)
//                                                        .color(android.graphics.Color.BLUE)
//                                                        .width(5f)
//                                                )
//
//                                                // Zoom to show the route
//                                                mapViewHelper.getRouteBounds()?.let { bounds ->
//                                                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100))
//                                                }
//                                            }
//                                        }
//
//                                        // Speak the directions
//                                        textToSpeech.speak(direction)
//
//                                        // Reset session token for Places API for proper billing
//                                        navigationManager.generateNewSessionToken()
//                                    } catch (e: Exception) {
//                                        errorMessage = "Navigation error: ${e.message}"
//                                    } finally {
//                                        isLoading = false
//                                    }
//                                }
//                            }
//                            .padding(16.dp),
//                        maxLines = 1,
//                        overflow = TextOverflow.Ellipsis
//                    )
//                    Divider()
//                }
//            }
//        }
//
//        // Map view
//        Box(
//            modifier = Modifier
//                .weight(1f)
//                .fillMaxWidth()
//        ) {
//            // GoogleMap implementation
//            AndroidView(
//                factory = { ctx ->
//                    MapView(ctx).also { mv ->
//                        mv.onCreate(null)
//                        mv.onResume()
//                        mv.getMapAsync { map ->
//                            googleMap = map
//
//                            // Initial map setup
//                            map.uiSettings.apply {
//                                isZoomControlsEnabled = true
//                                isCompassEnabled = true
//                                isMyLocationButtonEnabled = true
//                                isMapToolbarEnabled = true
//                            }
//
//                            // Set initial camera position to current location if available
//                            currentLocation?.let { location ->
//                                val latLng = LatLng(location.latitude, location.longitude)
//                                map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
//                                map.addMarker(MarkerOptions().position(latLng).title("Current location"))
//                            }
//                        }
//                        mapView = mv
//                    }
//                },
//                modifier = Modifier
//                    .fillMaxSize()
//                    .padding(vertical = 8.dp),
//                update = { mv ->
//                    // Update map with current location when it changes
//                    currentLocation?.let { location ->
//                        val latLng = LatLng(location.latitude, location.longitude)
//                        googleMap?.let { map ->
//                            // Only update camera if not navigating
//                            if (!isNavigating) {
//                                map.animateCamera(CameraUpdateFactory.newLatLng(latLng))
//                            }
//                        }
//                    }
//                }
//            )
//
//            // Show current direction on top of map
//            currentDirection?.let {
//                Card(
//                    modifier = Modifier
//                        .align(Alignment.BottomCenter)
//                        .fillMaxWidth()
//                        .padding(16.dp),
//                ) {
//                    Column(
//                        modifier = Modifier.padding(16.dp)
//                    ) {
//                        Text(
//                            text = "Current Direction",
//                            style = MaterialTheme.typography.titleMedium
//                        )
//                        Text(
//                            text = it,
//                            style = MaterialTheme.typography.bodyMedium
//                        )
//                    }
//                }
//            }
//
//            // Loading indicator
//            if (isLoading) {
//                CircularProgressIndicator(
//                    modifier = Modifier.align(Alignment.Center)
//                )
//            }
//        }
//
//        // Voice search button
//        Button(
//            onClick = {
//                speechRecognizer.startListening { spokenText ->
//                    searchQuery = spokenText
//                    scope.launch {
//                        predictions = navigationManager.findPlace(spokenText, currentLocation)
//                    }
//                }
//            },
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(vertical = 16.dp),
//            enabled = !isLoading
//        ) {
//            Text("Speak Destination")
//        }
//    }
//
//    // Handle lifecycle events for MapView
//    DisposableEffect(Unit) {
//        onDispose {
//            mapView?.onDestroy()
//            locationManager.stopLocationUpdates()
//        }
//    }
//}

//
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import androidx.navigation.NavController
//import com.google.android.gms.maps.model.CameraPosition
//import com.google.android.gms.maps.model.LatLng
//import com.google.maps.android.compose.*
//import kotlinx.coroutines.launch
//import project.prem.smartglasses.utils.LocationManager
//import project.prem.smartglasses.utils.NavigationManager
//import project.prem.smartglasses.utils.SpeechRecognizer
//import project.prem.smartglasses.utils.TextToSpeech
//
//@Composable
//fun MapsScreen(navController: NavController) {
//    val context = LocalContext.current
//    var searchText by remember { mutableStateOf("") }
//    var isNavigating by remember { mutableStateOf(false) }
//    var navigationInstruction by remember { mutableStateOf("") }
//    var routePolyline by remember { mutableStateOf<List<LatLng>>(emptyList()) }
//    val locationManager = remember { LocationManager(context) }
//    val navigationManager = remember { NavigationManager(context) }
//    val speechRecognizer = remember { SpeechRecognizer(context) }
//    val textToSpeech = remember { TextToSpeech(context) }
//
//    val currentLocation = remember { mutableStateOf(LatLng(37.7749, -122.4194)) }
//    val scope = rememberCoroutineScope()
//
//    LaunchedEffect(Unit) {
//        locationManager.getCurrentLocation()?.let {
//            currentLocation.value = LatLng(it.latitude, it.longitude)
//        }
//    }
//
//    Box(modifier = Modifier.fillMaxSize()) {
//        // Google Map
//        GoogleMap(
//            modifier = Modifier.fillMaxSize(),
//            cameraPositionState = rememberCameraPositionState {
//                position = CameraPosition.fromLatLngZoom(currentLocation.value, 15f)
//            }
//        ) {
//            if (routePolyline.isNotEmpty()) {
//                Polyline(
//                    points = routePolyline,
//                    color = MaterialTheme.colorScheme.primary,
//                    width = 8f
//                )
//            }
//        }
//
//        // Search Bar
//        TextField(
//            value = searchText,
//            onValueChange = { searchText = it },
//            placeholder = { Text("Search for a destination") },
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(16.dp)
//                .align(Alignment.TopCenter)
//        )
//
//        // Voice Input Button
//        FloatingActionButton(
//            onClick = {
//                speechRecognizer.startListening { result ->
//                    searchText = result
//                    // Start navigation
//                    scope.launch {
//                    navigationManager.findPlace(result)?.let { place ->
//                        navigationManager.getDirections(
//                            currentLocation.value,
//                            LatLng(place.latitude, place.longitude)
//                        )?.let { directions ->
//                            isNavigating = true
//                            navigationInstruction =
//                                directions.steps.firstOrNull()?.instruction ?: ""
//                            routePolyline = directions.route
//                            textToSpeech.speak(navigationInstruction)
//                        }
//                    }
//                }
//                }
//            },
//            modifier = Modifier
//                .align(Alignment.BottomCenter)
//                .padding(bottom = 16.dp)
//        ) {
//            Text("🎤")
//        }
//
//        // Navigation Instructions
//        if (isNavigating) {
//            Card(
//                modifier = Modifier
//                    .align(Alignment.BottomCenter)
//                    .fillMaxWidth()
//                    .padding(16.dp)
//            ) {
//                Column(
//                    modifier = Modifier.padding(16.dp),
//                    horizontalAlignment = Alignment.CenterHorizontally
//                ) {
//                    Text(navigationInstruction)
//                    Button(
//                        onClick = {
//                            isNavigating = false
//                            routePolyline = emptyList()
//                        },
//                        colors = ButtonDefaults.buttonColors(
//                            containerColor = MaterialTheme.colorScheme.error
//                        )
//                    ) {
//                        Text("END NAVIGATION")
//                    }
//                }
//            }
//        }
//    }
//}
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.lazy.LazyColumn
//import androidx.compose.foundation.lazy.items
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.unit.dp
//import androidx.navigation.NavController
//import com.google.android.gms.maps.CameraUpdateFactory
//import com.google.android.gms.maps.model.CameraPosition
//import com.google.android.gms.maps.model.LatLng
//import com.google.maps.android.compose.*
//import kotlinx.coroutines.*
//import project.prem.smartglasses.api.PlaceResult
//import project.prem.smartglasses.api.Prediction
//import project.prem.smartglasses.ml.ObjectDetectionModel
//import project.prem.smartglasses.utils.*
//import kotlin.math.absoluteValue
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun MapsScreen(navController: NavController) {
//    val context = LocalContext.current
//    val scope = rememberCoroutineScope()
//    val locationManager = remember { LocationManager(context) }
//    val navigationManager = remember { NavigationManager(context) }
//    val speechRecognizer = remember { SpeechRecognizer(context) }
//    val tts = remember { TextToSpeech(context) }
//    val objectDetectionModel = remember { ObjectDetectionModel(context) }
//    val cameraManager = remember { CameraManager(context) }
//
//    var userLocation by remember { mutableStateOf<LatLng?>(null) }
//    var destination by remember { mutableStateOf<LatLng?>(null) }
//    var isListening by remember { mutableStateOf(false) }
//    var isNavigating by remember { mutableStateOf(false) }
//    var currentDirections by remember { mutableStateOf<List<String>>(emptyList()) }
//    var currentDirectionIndex by remember { mutableStateOf(0) }
//    var searchQuery by remember { mutableStateOf("") }
//    var predictions by remember { mutableStateOf<List<Prediction>>(emptyList()) }
//    var showPredictions by remember { mutableStateOf(false) }
//    var searchStatus by remember { mutableStateOf<String?>(null) }
//    var lastSpokenObject by remember { mutableStateOf("") }
//    var isProcessingFrame by remember { mutableStateOf(false) }
//    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
//
//    val defaultLocation = LatLng(0.0, 0.0)
//    val cameraPositionState = rememberCameraPositionState {
//        position = CameraPosition.fromLatLngZoom(defaultLocation, 15f)
//    }
//
//    DisposableEffect(Unit) {
//        onDispose {
//            speechRecognizer.destroy()
//            tts.shutdown()
//            navigationManager.cleanup()
//            objectDetectionModel.shutdown()
//            cameraManager.shutdown()
//        }
//    }
//
//    LaunchedEffect(Unit) {
//        val location = locationManager.getCurrentLocation()
//        userLocation = location
//        cameraPositionState.animate(
//            CameraUpdateFactory.newLatLngZoom(location, 15f)
//        )
//
//        cameraManager.initialize()
//        cameraManager.startCamera(
//            previewView = null,
//            frameInterval = 1000L
//        ) { bitmap ->
//            if (!isProcessingFrame && isNavigating) {
//                isProcessingFrame = true
//                scope.launch(Dispatchers.Default) {
//                    try {
//                        val detectedObjects = objectDetectionModel.detectObjects(bitmap)
//                        detectedObjects.forEach { obj ->
//                            if (obj.angle.absoluteValue <= 45.0 && obj.confidence >= 0.7f) {
//                                val announcement = "${obj.label} detected at ${
//                                    when {
//                                        obj.angle > 22.5 && obj.angle <= 67.5 -> "front-right"
//                                        obj.angle > 67.5 && obj.angle <= 112.5 -> "right"
//                                        obj.angle > -112.5 && obj.angle <= -67.5 -> "left"
//                                        obj.angle > -67.5 && obj.angle <= -22.5 -> "front-left"
//                                        else -> "ahead"
//                                    }
//                                }"
//
//                                if (announcement != lastSpokenObject) {
//                                    tts.speak(announcement)
//                                    lastSpokenObject = announcement
//                                }
//                            }
//                        }
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                    } finally {
//                        isProcessingFrame = false
//                    }
//                }
//            }
//        }
//    }
//
//
//    LaunchedEffect(currentDirections, isNavigating) {
//        if (isNavigating && currentDirections.isNotEmpty() && currentDirectionIndex < currentDirections.size) {
//            tts.speak(currentDirections[currentDirectionIndex])
//        }
//    }
//
//    LaunchedEffect(searchQuery) {
//        if (searchQuery.length >= 3) {
//            val newPredictions = navigationManager.getAutocompletePredictions(searchQuery)
//            predictions = newPredictions
//            showPredictions = newPredictions.isNotEmpty()
//        } else {
//            predictions = emptyList()
//            showPredictions = false
//        }
//    }
//
//    Column(modifier = Modifier.fillMaxSize()) {
//        TextField(
//            value = searchQuery,
//            onValueChange = { searchQuery = it },
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(16.dp),
//            placeholder = { Text("Search location or speak destination") },
//            colors = TextFieldDefaults.colors(
//                focusedContainerColor = MaterialTheme.colorScheme.surface,
//                unfocusedContainerColor = MaterialTheme.colorScheme.surface
//            )
//        )
//
//        if (showPredictions) {
//            LazyColumn(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .heightIn(max = 200.dp)
//                    .background(MaterialTheme.colorScheme.surface)
//            ) {
//                items(predictions) { prediction ->
//                    Text(
//                        text = prediction.description,
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .clickable {
//                                scope.launch {
//                                    searchStatus = "Getting place details..."
//                                    val placeDetails = navigationManager.getPlaceDetails(prediction.place_id)
//                                    if (placeDetails != null) {
//                                        val placeLatLng = LatLng(
//                                            placeDetails.geometry.location.lat,
//                                            placeDetails.geometry.location.lng
//                                        )
//                                        destination = placeLatLng
//                                        searchQuery = placeDetails.formatted_address
//                                        showPredictions = false
//
//                                        navigationManager.getDirectionsTo(placeDetails) { directions, points ->
//                                            currentDirections = directions
//                                            routePoints = points
//                                            currentDirectionIndex = 0
//                                            isNavigating = true
//                                            searchStatus = null
//
//                                            if (directions.isNotEmpty()) {
//                                                tts.speak("Starting navigation. ${directions.first()}")
//                                            }
//                                        }
//                                    }
//                                }
//                            }
//                            .padding(16.dp)
//                    )
//                }
//            }
//        }
//
//        Box(modifier = Modifier.weight(1f)) {
//            GoogleMap(
//                modifier = Modifier.fillMaxSize(),
//                cameraPositionState = cameraPositionState,
//                properties = MapProperties(
//                    isMyLocationEnabled = true,
//                    mapType = MapType.NORMAL
//                ),
//                uiSettings = MapUiSettings(
//                    zoomControlsEnabled = true,
//                    myLocationButtonEnabled = true,
//                    mapToolbarEnabled = true
//                )
//            ) {
//                userLocation?.let { location ->
//                    Marker(
//                        state = MarkerState(position = location),
//                        title = "Your Location"
//                    )
//                }
//
//                destination?.let { dest ->
//                    Marker(
//                        state = MarkerState(position = dest),
//                        title = "Destination"
//                    )
//                }
//
//                if (routePoints.isNotEmpty()) {
//                    Polyline(
//                        points = routePoints,
//                        color = Color.Blue,
//                        width = 5f
//                    )
//                }
//            }
//
//            searchStatus?.let { status ->
//                Text(
//                    text = status,
//                    modifier = Modifier
//                        .align(Alignment.TopCenter)
//                        .padding(16.dp)
//                        .background(
//                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
//                            shape = MaterialTheme.shapes.medium
//                        )
//                        .padding(8.dp),
//                    style = MaterialTheme.typography.bodyMedium
//                )
//            }
//        }
//
//        Column(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(16.dp),
//            verticalArrangement = Arrangement.spacedBy(8.dp)
//        ) {
//            if (isNavigating && currentDirections.isNotEmpty()) {
//                Text(
//                    text = currentDirections[currentDirectionIndex],
//                    style = MaterialTheme.typography.bodyLarge,
//                    modifier = Modifier.padding(bottom = 8.dp)
//                )
//
//                Row(
//                    modifier = Modifier.fillMaxWidth(),
//                    horizontalArrangement = Arrangement.SpaceBetween
//                ) {
//                    Button(
//                        onClick = {
//                            if (currentDirectionIndex > 0) {
//                                currentDirectionIndex--
//                                tts.speak(currentDirections[currentDirectionIndex])
//                            }
//                        },
//                        enabled = currentDirectionIndex > 0
//                    ) {
//                        Text("Previous")
//                    }
//
//                    Button(
//                        onClick = {
//                            if (currentDirectionIndex < currentDirections.size - 1) {
//                                currentDirectionIndex++
//                                tts.speak(currentDirections[currentDirectionIndex])
//                            }
//                        },
//                        enabled = currentDirectionIndex < currentDirections.size - 1
//                    ) {
//                        Text("Next")
//                    }
//                }
//            }
//
//            Button(
//                onClick = {
//                    isListening = true
//                    searchStatus = "Listening for destination..."
//                    speechRecognizer.startListening { spokenText ->
//                        searchQuery = spokenText
//                        scope.launch {
//                            try {
//                                searchStatus = "Searching for: $spokenText"
//                                val place = navigationManager.searchPlace(spokenText)
//
//                                if (place != null) {
//                                    destination = LatLng(
//                                        place.geometry.location.lat,
//                                        place.geometry.location.lng
//                                    )
//
//                                    navigationManager.getDirectionsTo(place) { directions, points ->
//                                        currentDirections = directions
//                                        routePoints = points
//                                        currentDirectionIndex = 0
//                                        isNavigating = true
//                                        searchStatus = null
//
//                                        if (directions.isNotEmpty()) {
//                                            tts.speak("Starting navigation. ${directions.first()}")
//                                        }
//                                    }
//                                } else {
//                                    searchStatus = "Location not found"
//                                    tts.speak("Sorry, I couldn't find that location")
//                                    delay(2000)
//                                    searchStatus = null
//                                }
//                            } catch (e: Exception) {
//                                searchStatus = "Error finding location"
//                                tts.speak("Sorry, there was an error finding the location")
//                                delay(2000)
//                                searchStatus = null
//                            }
//                        }
//                        isListening = false
//                    }
//                },
//                modifier = Modifier.fillMaxWidth(),
//                enabled = !isListening && userLocation != null
//            ) {
//                Text(if (isListening) "Listening..." else "Speak Destination")
//            }
//
//            if (isNavigating) {
//                Button(
//                    onClick = {
//                        isNavigating = false
//                        currentDirections = emptyList()
//                        currentDirectionIndex = 0
//                        destination = null
//                        searchStatus = null
//                        searchQuery = ""
//                        routePoints = emptyList()
//                        tts.speak("Navigation stopped")
//                    },
//                    modifier = Modifier.fillMaxWidth()
//                ) {
//                    Text("Stop Navigation")
//                }
//            }
//
//            Button(
//                onClick = { navController.navigateUp() },
//                modifier = Modifier.fillMaxWidth()
//            ) {
//                Text("Back")
//            }
//        }
//    }
//}