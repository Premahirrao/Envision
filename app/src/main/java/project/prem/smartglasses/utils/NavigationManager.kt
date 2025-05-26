package project.prem.smartglasses.utils


import android.content.Context
import android.location.Location
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.util.GeoPoint
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class NavigationManager(private val context: Context) {
    private val TAG = "NavigationManager"
    private val osmMapHelper = OSMMapHelper(context)

    // Nominatim base URL
    private val NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org/search"
    // Store navigation state
    private var isNavigating = false
    private var currentInstructionIndex = 0
    private var navigationInstructions = listOf<String>()
    private var currentDestination: Pair<Double, Double>? = null


    suspend fun getDirections(from: Location?, destination: String): String = withContext(Dispatchers.IO) {
        try {
            val fromLocation = from ?: return@withContext "Error: Current location not available"

            // First, geocode the destination
            val geocodeResult = geocodeAddress(destination)
            if (geocodeResult.isEmpty()) {
                return@withContext "Error: Could not find location for $destination"
            }

            // Get the first result
            val destinationPoint = geocodeResult.first()
            Log.d(TAG, "Getting directions from ${fromLocation.latitude},${fromLocation.longitude} to ${destinationPoint.first},${destinationPoint.second}")

            // Store the current destination for map drawing
            currentDestination = Pair(destinationPoint.first, destinationPoint.second)

            // Create road manager for routing
            // Get route polyline using OSMMapHelper
            val destGeoPoint = GeoPoint(destinationPoint.first, destinationPoint.second)
            val polyline = osmMapHelper.getRoutePolyline(fromLocation, destGeoPoint)

            if (polyline == null) {
                return@withContext "Error: Could not calculate route to $destination"
            }
            // Get all navigation instructions
            navigationInstructions = osmMapHelper.getRoutingInstructions()

            // Reset navigation state
            isNavigating = true
            currentInstructionIndex = 0

            if (navigationInstructions.isNotEmpty()) {
                val firstInstruction = navigationInstructions.first()
                Log.d(TAG, "First navigation instruction: $firstInstruction")
                return@withContext firstInstruction            }

            val fallbackInstruction = "Starting navigation to $destination"
            Log.d(TAG, "Using fallback instruction: $fallbackInstruction")
            return@withContext fallbackInstruction
        } catch (e: Exception) {
            Log.e(TAG, "Error getting directions", e)
            "Error: ${e.localizedMessage}"
        }
    }

    fun getNextNavInstruction(): String? {
        if (!isNavigating || navigationInstructions.isEmpty()) {
            return null
        }

        if (currentInstructionIndex < navigationInstructions.size - 1) {
            currentInstructionIndex++
            val instruction = navigationInstructions[currentInstructionIndex]
            Log.d(TAG, "Next instruction: $instruction")
            return instruction        } else if (currentInstructionIndex == navigationInstructions.size - 1) {
            // Final instruction
            isNavigating = false
            val finalInstruction = "You have arrived at your destination"
            Log.d(TAG, "Final instruction: $finalInstruction")
            return finalInstruction        }

        return null
    }

    fun getCurrentInstruction(): String? {
        if (!isNavigating || navigationInstructions.isEmpty() || currentInstructionIndex >= navigationInstructions.size) {
            return null
        }
        val instruction = navigationInstructions[currentInstructionIndex]
        Log.d(TAG, "Current instruction: $instruction")
        return instruction    }

    fun getCurrentDestination(): Pair<Double, Double>? {
        return currentDestination
    }

    fun isCurrentlyNavigating(): Boolean {
        return isNavigating
    }

    suspend fun findPlace(query: String, location: Location?): List<String> = withContext(Dispatchers.IO) {
        try {
            if (query.length < 2) return@withContext emptyList<String>()

            val places = mutableListOf<String>()
            val geocodeResults = geocodeAddress(query)

// Filter for places in India (prioritize Indian results)
            val indiaResults = geocodeResults.filter { it.third.contains("India", ignoreCase = true) }
            val resultsToUse = if (indiaResults.isNotEmpty()) indiaResults else geocodeResults

            for ((_, _, displayName) in resultsToUse) {                places.add(displayName)
            }

            places
        } catch (e: Exception) {
            Log.e(TAG, "Error finding places", e)
            emptyList()
        }
    }

    // Geocode an address and return location details
    suspend fun findFirstPlace(query: String, location: Location?): Triple<Double, Double, String>? = withContext(Dispatchers.IO) {
        try {
            if (query.length < 2) return@withContext null

            val geocodeResults = geocodeAddress(query)
            if (geocodeResults.isEmpty()) return@withContext null

            // Filter for places in India (prioritize Indian results)
            val indiaResults = geocodeResults.filter { it.third.contains("India", ignoreCase = true) }
            val resultsToUse = if (indiaResults.isNotEmpty()) indiaResults else geocodeResults

            if (resultsToUse.isNotEmpty()) {
                return@withContext resultsToUse.first()
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "Error finding first place", e)
            null
        }
    }

    // Geocode an address using Nominatim
    private suspend fun geocodeAddress(address: String): List<Triple<Double, Double, String>> = suspendCancellableCoroutine { continuation ->
        try {
            val requestQueue = Volley.newRequestQueue(context)
            val encodedAddress = URLEncoder.encode(address, "UTF-8")
            // Add viewbox and countrycodes parameters to improve results for India
            // We'll use a large viewbox covering most of India, but the API will still return global results
            val viewbox = "68.0,37.0,98.0,5.0" // Covers most of Indian subcontinent
            val url = "$NOMINATIM_BASE_URL?q=$encodedAddress&format=json&limit=10&viewbox=$viewbox&bounded=0&countrycodes=in"

            Log.d(TAG, "Geocoding URL: $url")

            // Add User-Agent as required by Nominatim Usage Policy
            val request = object : JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                { response ->
                    try {
                        val results = mutableListOf<Triple<Double, Double, String>>()
                        Log.d(TAG, "Geocoding response received with ${response.length()} items")

                        for (i in 0 until response.length()) {
                            val item = response.getJSONObject(i)
                            val lat = item.getDouble("lat")
                            val lon = item.getDouble("lon")
                            val displayName = item.getString("display_name")
                            results.add(Triple(lat, lon, displayName))
                            Log.d(TAG, "Found place: $displayName at $lat,$lon")

                        }

                        continuation.resume(results)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing geocoding response", e)
                        continuation.resume(emptyList())
                    }
                },
                { error ->
                    Log.e(TAG, "Geocoding request failed", error)
                    continuation.resume(emptyList())
                }
            ) {
                override fun getHeaders(): MutableMap<String, String> {
                    val headers = HashMap<String, String>()
                    headers["User-Agent"] = "Navigator Android App"
                    return headers
                }
            }

            requestQueue.add(request)

        } catch (e: Exception) {
            Log.e(TAG, "Error in geocodeAddress", e)
            continuation.resumeWithException(e)
        }

    }
}
//
//
//import android.content.Context
//import android.location.Location
//import android.net.Uri
//import android.util.Log
//import project.prem.smartglasses.R
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import org.json.JSONObject
//import java.net.URL
//import java.net.URLEncoder
//import java.util.UUID
//
//class NavigationManager(private val context: Context) {
//    private val apiKey = context.getString(R.string.map_api_key)
//    private val TAG = "NavigationManager"
//
//    // Session token for Places API (helps with billing)
//    private var sessionToken = UUID.randomUUID().toString()
//
//    suspend fun getDirections(from: Location?, destination: String): String = withContext(Dispatchers.IO) {
//        try {
//            val fromLocation = from ?: return@withContext "Error: Current location not available"
//            val encodedDest = URLEncoder.encode(destination, "UTF-8")
//
//            Log.d(TAG, "Getting directions from ${fromLocation.latitude},${fromLocation.longitude} to $destination")
//
//            val url = URL(
//                "https://maps.googleapis.com/maps/api/directions/json" +
//                        "?origin=${fromLocation.latitude},${fromLocation.longitude}" +
//                        "&destination=$encodedDest" +
//                        "&key=$apiKey"
//            )
//
//            val response = url.readText()
//            Log.d(TAG, "Directions API response: $response")
//
//            val jsonResponse = JSONObject(response)
//
//            if (jsonResponse.getString("status") == "OK") {
//                val routes = jsonResponse.getJSONArray("routes")
//                if (routes.length() > 0) {
//                    val legs = routes.getJSONObject(0).getJSONArray("legs")
//                    if (legs.length() > 0) {
//                        val steps = legs.getJSONObject(0).getJSONArray("steps")
//                        if (steps.length() > 0) {
//                            return@withContext steps.getJSONObject(0)
//                                .getString("html_instructions")
//                                .replace("<[^>]*>".toRegex(), "")
//                        }
//                    }
//                }
//            }
//            "Unable to find directions"
//        } catch (e: Exception) {
//            Log.e(TAG, "Error getting directions", e)
//            "Error: ${e.localizedMessage}"
//        }
//    }
//
//    suspend fun findPlace(query: String, location: Location?): List<String> = withContext(Dispatchers.IO) {
//        try {
//            if (query.length < 2) return@withContext emptyList<String>()
//
//            Log.d(TAG, "Searching for places with query: $query")
//
//            // Build the URL with proper parameters
//            val urlBuilder = Uri.parse("https://maps.googleapis.com/maps/api/place/autocomplete/json").buildUpon()
//            urlBuilder.appendQueryParameter("input", query)
//            urlBuilder.appendQueryParameter("key", apiKey)
//            urlBuilder.appendQueryParameter("sessiontoken", sessionToken)
//
//            // Add location bias if available (important for relevant results)
//            if (location != null) {
//                Log.d(TAG, "Adding location bias: ${location.latitude},${location.longitude}")
//                urlBuilder.appendQueryParameter("location", "${location.latitude},${location.longitude}")
//                urlBuilder.appendQueryParameter("radius", "50000") // 50km radius
//                urlBuilder.appendQueryParameter("strictbounds", "false") // Allow some results outside radius but prioritize within
//            }
//
//            val url = URL(urlBuilder.build().toString())
//            Log.d(TAG, "Places API URL: ${url.toString()}")
//
//            val response = url.readText()
//            Log.d(TAG, "Places API response: $response")
//
//            val jsonResponse = JSONObject(response)
//            val status = jsonResponse.getString("status")
//
//            if (status == "OK") {
//                val predictions = jsonResponse.getJSONArray("predictions")
//                val places = mutableListOf<String>()
//
//                Log.d(TAG, "Found ${predictions.length()} place predictions")
//
//                for (i in 0 until predictions.length()) {
//                    val description = predictions.getJSONObject(i).getString("description")
//                    places.add(description)
//                    Log.d(TAG, "Place prediction: $description")
//                }
//
//                if (places.isNotEmpty()) {
//                    // Generate a new session token for next search
//                    sessionToken = UUID.randomUUID().toString()
//                }
//
//                return@withContext places
//            } else {
//                Log.e(TAG, "Places API error: $status")
//                if (jsonResponse.has("error_message")) {
//                    Log.e(TAG, "Error message: ${jsonResponse.getString("error_message")}")
//                }
//                emptyList()
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Error finding places", e)
//            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
//            emptyList()
//        }
//    }
//
//    fun generateNewSessionToken() {
//        sessionToken = UUID.randomUUID().toString()
//    }
//}

//
//import android.content.Context
//import android.location.Location
//import android.util.Log
//import project.prem.smartglasses.R
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import org.json.JSONObject
//import java.net.URL
//import java.net.URLEncoder
//import java.util.UUID
//
//class NavigationManager(private val context: Context) {
//    private val apiKey = context.getString(R.string.map_api_key)
//    private val TAG = "NavigationManager"
//
//    // Session token for Places API (helps with billing)
//    private var sessionToken = UUID.randomUUID().toString()
//
//    suspend fun getDirections(from: Location?, destination: String): String = withContext(Dispatchers.IO) {
//        try {
//            val fromLocation = from ?: return@withContext "Error: Current location not available"
//            val encodedDest = URLEncoder.encode(destination, "UTF-8")
//
//            Log.d(TAG, "Getting directions from ${fromLocation.latitude},${fromLocation.longitude} to $destination")
//
//            val url = URL(
//                "https://maps.googleapis.com/maps/api/directions/json" +
//                        "?origin=${fromLocation.latitude},${fromLocation.longitude}" +
//                        "&destination=$encodedDest" +
//                        "&key=$apiKey"
//            )
//
//            val response = url.readText()
//            Log.d(TAG, "Directions API response: $response")
//
//            val jsonResponse = JSONObject(response)
//
//            if (jsonResponse.getString("status") == "OK") {
//                val routes = jsonResponse.getJSONArray("routes")
//                if (routes.length() > 0) {
//                    val legs = routes.getJSONObject(0).getJSONArray("legs")
//                    if (legs.length() > 0) {
//                        val steps = legs.getJSONObject(0).getJSONArray("steps")
//                        if (steps.length() > 0) {
//                            return@withContext steps.getJSONObject(0)
//                                .getString("html_instructions")
//                                .replace("<[^>]*>".toRegex(), "")
//                        }
//                    }
//                }
//            }
//            "Unable to find directions"
//        } catch (e: Exception) {
//            Log.e(TAG, "Error getting directions", e)
//            "Error: ${e.localizedMessage}"
//        }
//    }
//
//    suspend fun findPlace(query: String, location: Location?): List<String> = withContext(Dispatchers.IO) {
//        try {
//            if (query.length < 2) return@withContext emptyList<String>()
//
//            val encodedQuery = URLEncoder.encode(query, "UTF-8")
//
//            // Create a location bias parameter if location is available
//            val locationBias = if (location != null) {
//                "&location=${location.latitude},${location.longitude}&radius=50000"
//            } else {
//                ""
//            }
//
//            Log.d(TAG, "Searching for places: $query with location bias: $locationBias")
//
//            // Regenerate session token after each successful place selection
//            val url = URL(
//                "https://maps.googleapis.com/maps/api/place/autocomplete/json" +
//                        "?input=$encodedQuery" +
//                        locationBias +
//                        "&types=establishment" +
//                        "&components=country:in" +
//                        "&sessiontoken=$sessionToken" +
//                        "&key=$apiKey"
//            )
//
//            val response = url.readText()
//            Log.d(TAG, "Places API response: $response")
//
//            val jsonResponse = JSONObject(response)
//
//            if (jsonResponse.getString("status") == "OK") {
//                val predictions = jsonResponse.getJSONArray("predictions")
//                val places = mutableListOf<String>()
//
//                for (i in 0 until predictions.length()) {
//                    places.add(predictions.getJSONObject(i)
//                        .getString("description"))
//                }
//
//                if (places.isNotEmpty()) {
//                    // Generate a new session token for next search
//                    sessionToken = UUID.randomUUID().toString()
//                }
//
//                places
//            } else {
//                Log.e(TAG, "Places API error: ${jsonResponse.getString("status")}")
//                emptyList()
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Error finding places", e)
//            emptyList()
//        }
//    }
//
//    fun generateNewSessionToken() {
//        sessionToken = UUID.randomUUID().toString()
//    }
//}

//
//import android.content.Context
//import android.util.Log
//import com.google.android.gms.maps.model.LatLng
//import com.google.maps.GeoApiContext
//import com.google.maps.PlacesApi
//import com.google.maps.DirectionsApi
//import com.google.maps.model.PlacesSearchResult
//import com.google.maps.model.DirectionsResult
//import com.google.maps.model.TravelMode
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//import project.prem.smartglasses.R
//
//data class Place(
//    val placeId: String,
//    val name: String,
//    val address: String,
//    val latitude: Double,
//    val longitude: Double
//)
//
//data class DirectionStep(
//    val instruction: String,
//    val distance: String,
//    val maneuver: String? = null
//)
//
//data class Directions(
//    val steps: List<DirectionStep>,
//    val route: List<LatLng>,
//    val distance: String,
//    val duration: String
//)
//
//class NavigationManager(private val context: Context) {
//    private val apiKey = context.getString(R.string.map_api_key)
//    private val geoContext: GeoApiContext = GeoApiContext.Builder()
//        .apiKey(apiKey)
//        .build()
//
//    suspend fun findPlace(query: String): Place? = withContext(Dispatchers.IO) {
//        try {
//            val request = PlacesApi.textSearchQuery(geoContext, query)
//            val response = request.await()
//
//            if (response.results.isNotEmpty()) {
//                val place = response.results[0]
//                Place(
//                    placeId = place.placeId,
//                    name = place.name,
//                    address = place.formattedAddress,
//                    latitude = place.geometry.location.lat,
//                    longitude = place.geometry.location.lng
//                )
//            } else {
//                null
//            }
//        } catch (e: Exception) {
//            Log.e("NavigationManager", "Error finding place", e)
//            null
//        }
//    }
//
//    suspend fun getDirections(origin: LatLng, destination: LatLng): Directions? = withContext(Dispatchers.IO) {
//        try {
//            val request = DirectionsApi.newRequest(geoContext)
//                .origin(com.google.maps.model.LatLng(origin.latitude, origin.longitude))
//                .destination(com.google.maps.model.LatLng(destination.latitude, destination.longitude))
//                .mode(TravelMode.DRIVING)
//
//            val response = request.await()
//
//            if (response.routes.isNotEmpty()) {
//                val route = response.routes[0]
//                val leg = route.legs[0]
//
//                val steps = leg.steps.map { step ->
//                    DirectionStep(
//                        instruction = step.htmlInstructions.replace(Regex("<[^>]*>"), ""),
//                        distance = step.distance.humanReadable,
//                        maneuver = step.maneuver
//                    )
//                }
//
//                val routePoints = route.overviewPolyline.decodePath().map {
//                    LatLng(it.lat, it.lng)
//                }
//
//                Directions(
//                    steps = steps,
//                    route = routePoints,
//                    distance = leg.distance.humanReadable,
//                    duration = leg.duration.humanReadable
//                )
//            } else {
//                null
//            }
//        } catch (e: Exception) {
//            Log.e("NavigationManager", "Error getting directions", e)
//            null
//        }
//    }
//}
//
//import android.content.Context
//import project.prem.smartglasses.R
//import project.prem.smartglasses.api.PlacesApiService
//import com.google.android.gms.maps.model.LatLng
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.SupervisorJob
//import kotlinx.coroutines.cancel
//import kotlinx.coroutines.coroutineScope
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import okhttp3.OkHttpClient
//import okhttp3.logging.HttpLoggingInterceptor
//import project.prem.smartglasses.api.PlaceResult
//import project.prem.smartglasses.api.Prediction
//import retrofit2.Retrofit
//import retrofit2.converter.gson.GsonConverterFactory
//import java.util.concurrent.TimeUnit
//import kotlin.coroutines.resume
//import kotlin.coroutines.suspendCoroutine
//import kotlin.plus
//
//class NavigationManager(private val context: Context) {
//
//    private val apiKey = context.getString(R.string.map_api_key)
//    private val placesApiService: PlacesApiService
//    private val locationManager = LocationManager(context)
//
//    init {
//        val logging = HttpLoggingInterceptor().apply {
//            level = HttpLoggingInterceptor.Level.BODY
//        }
//
//        val client = OkHttpClient.Builder()
//            .addInterceptor(logging)
//            .connectTimeout(15, TimeUnit.SECONDS)
//            .readTimeout(15, TimeUnit.SECONDS)
//            .build()
//
//        val retrofit = Retrofit.Builder()
//            .baseUrl("https://maps.googleapis.com/maps/api/")
//            .client(client)
//            .addConverterFactory(GsonConverterFactory.create())
//            .build()
//
//        placesApiService = retrofit.create(PlacesApiService::class.java)
//    }
//
//    suspend fun getAutocompletePredictions(query: String): List<Prediction> {
//        return try {
//            val currentLocation = locationManager.getCurrentLocation()
//            val locationString = "${currentLocation.latitude},${currentLocation.longitude}"
//            val response = placesApiService.getPlacePredictions(
//                input = query,
//                apiKey = apiKey,
//                location = locationString
//            )
//
//            if (response.status == "OK") {
//                response.predictions
//            } else {
//                emptyList()
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            emptyList()
//        }
//    }
//
//
//    suspend fun getPlaceDetails(placeId: String): PlaceResult? {
//        return try {
//            val response = placesApiService.getPlaceDetails(
//                placeId = placeId,
//                apiKey = apiKey
//            )
//            if (response.status == "OK") {
//                response.result
//            } else {
//                null
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            null
//        }
//    }
//    suspend fun searchPlace(query: String): PlaceResult? {
//        return try {
//            val response = placesApiService.textSearchPlace(
//                query = query,
//                apiKey = apiKey
//            )
//            when (response.status) {
//                "OK" -> response.results.firstOrNull()
//                else -> null
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            null
//        }
//    }
//    private val navigationScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
//
//    fun getDirectionsTo(
//        place: PlaceResult,
//        callback: (directions: List<String>, points: List<LatLng>) -> Unit
//    ) {
//        navigationScope.launch {
//            try {
//                val userLocation = locationManager.getCurrentLocation()
//                val origin = "${userLocation.latitude},${userLocation.longitude}"
//                val destination = "${place.geometry.location.lat},${place.geometry.location.lng}"
//
//                val response = withContext(Dispatchers.IO) {
//                    placesApiService.getDirections(
//                        origin = origin,
//                        destination = destination,
//                        apiKey = apiKey
//                    )
//                }
//
//                if (response.status == "OK") {
//                    val directions = mutableListOf<String>()
//                    val allPoints = mutableListOf<LatLng>()
//
//                    response.routes.firstOrNull()?.legs?.forEach { leg ->
//                        leg.steps.forEach { step ->
//                            directions.add(cleanHtmlTags(step.html_instructions))
//                            allPoints.addAll(decodePolyline(step.polyline.points))
//                        }
//                    }
//
//                    callback(directions, allPoints)
//                } else {
//                    callback(emptyList(), emptyList())
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//                callback(emptyList(), emptyList())
//            }
//        }
//    }
//
//
//
//    private fun cleanHtmlTags(html: String): String {
//        return html.replace(Regex("<[^>]*>"), "")
//    }
//
//    private fun decodePolyline(encoded: String): List<LatLng> {
//        val poly = ArrayList<LatLng>()
//        var index = 0
//        val len = encoded.length
//        var lat = 0
//        var lng = 0
//
//        while (index < len) {
//            var b: Int
//            var shift = 0
//            var result = 0
//            do {
//                b = encoded[index++].code - 63
//                result = result or (b and 0x1f shl shift)
//                shift += 5
//            } while (b >= 0x20)
//            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
//            lat += dlat
//
//            shift = 0
//            result = 0
//            do {
//                b = encoded[index++].code - 63
//                result = result or (b and 0x1f shl shift)
//                shift += 5
//            } while (b >= 0x20)
//            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
//            lng += dlng
//
//            val latLng = LatLng(lat / 1E5, lng / 1E5)
//            poly.add(latLng)
//        }
//
//        return poly
//    }
//
//    fun cleanup() {
//        navigationScope.cancel()
//    }
//}
//
//import android.Manifest
//import android.content.Context
//import android.content.pm.PackageManager
//import androidx.core.content.ContextCompat
//import com.google.android.gms.maps.model.LatLng
//import com.google.android.libraries.places.api.Places
//import com.google.android.libraries.places.api.model.Place
//import com.google.android.libraries.places.api.model.AutocompletePrediction
//import com.google.android.libraries.places.api.net.*
//import com.google.maps.DirectionsApi
//import com.google.maps.GeoApiContext
//import com.google.maps.model.DirectionsResult
//import com.google.maps.model.TravelMode
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.tasks.await
//import kotlinx.coroutines.withContext
//import com.google.android.libraries.places.api.model.LocationBias // Import LocationBias
//import com.google.android.libraries.places.api.model.RectangularBounds // Import RectangularBounds
//import com.google.android.libraries.places.api.net.*
//import project.prem.smartglasses.R
//
//class NavigationManager(private val context: Context) {
//    private val placesClient: PlacesClient
//    private val geoApiContext: GeoApiContext
//    private val locationManager = LocationManager(context)
//
//    init {
//        val apiKey = context.getString(R.string.map_api_key)
//        Places.initialize(context, apiKey)
//        placesClient = Places.createClient(context)
//        geoApiContext = GeoApiContext.Builder()
//            .apiKey(apiKey)
//            .build()
//    }
//
//    private fun hasLocationPermission(): Boolean {
//        return ContextCompat.checkSelfPermission(
//            context,
//            Manifest.permission.ACCESS_FINE_LOCATION
//        ) == PackageManager.PERMISSION_GRANTED
//    }
//
//    suspend fun getAutocompletePredictions(query: String): List<AutocompletePrediction> = withContext(Dispatchers.IO) {
//        try {
//            if (!hasLocationPermission()) return@withContext emptyList()
//
//            locationManager.getCurrentLocation { location ->
//                val bias = LocationBias.newLocationBias(
//                    RectangularBounds.newInstance(
//                        LatLng(location.latitude - 0.1, location.longitude - 0.1),
//                        LatLng(location.latitude + 0.1, location.longitude + 0.1)
//                    )
//                )
//
//                val request = FindAutocompletePredictionsRequest.builder()
//                    .setLocationBias(bias)
//                    .setQuery(query)
//                    .setCountries("US") // Add more countries as needed
//                    .build()
//
//                val response = placesClient.findAutocompletePredictions(request).await()
//                return@withContext response.autocompletePredictions
//            }
//            return@withContext emptyList()
//        } catch (e: Exception) {
//            e.printStackTrace()
//            return@withContext emptyList()
//        }
//    }
//
//    suspend fun getPlaceFromPrediction(prediction: AutocompletePrediction): Place? = withContext(Dispatchers.IO) {
//        try {
//            val placeFields = listOf(
//                Place.Field.ID,
//                Place.Field.NAME,
//                Place.Field.LAT_LNG,
//                Place.Field.ADDRESS,
//                Place.Field.TYPES
//            )
//
//            val request = FetchPlaceRequest.builder(prediction.placeId, placeFields).build()
//            val response = placesClient.fetchPlace(request).await()
//            return@withContext response.place
//        } catch (e: Exception) {
//            e.printStackTrace()
//            return@withContext null
//        }
//    }
//
//    suspend fun searchPlace(query: String): Place? = withContext(Dispatchers.IO) {
//        try {
//            val predictions = getAutocompletePredictions(query)
//            if (predictions.isNotEmpty()) {
//                return@withContext getPlaceFromPrediction(predictions.first())
//            }
//            return@withContext null
//        } catch (e: Exception) {
//            e.printStackTrace()
//            return@withContext null
//        }
//    }
//
//    suspend fun getDirectionsTo(destination: Place, onDirectionsReceived: (List<String>) -> Unit) {
//        try {
//            locationManager.getCurrentLocation { currentLocation ->
//                val origin = com.google.maps.model.LatLng(
//                    currentLocation.latitude,
//                    currentLocation.longitude
//                )
//
//                val dest = com.google.maps.model.LatLng(
//                    destination.latLng?.latitude ?: 0.0,
//                    destination.latLng?.longitude ?: 0.0
//                )
//
//                val result = DirectionsApi.newRequest(geoApiContext)
//                    .mode(TravelMode.WALKING)
//                    .origin(origin)
//                    .destination(dest)
//                    .alternatives(false)
//                    .await()
//
//                val directions = parseDirections(result)
//                onDirectionsReceived(directions)
//            }
//        } catch (e: Exception) {
//            e.printStackTrace()
//            onDirectionsReceived(listOf("Unable to get directions"))
//        }
//    }
//
//    private fun parseDirections(result: DirectionsResult): List<String> {
//        return result.routes.firstOrNull()?.legs?.firstOrNull()?.steps?.map { step ->
//            step.htmlInstructions
//                .replace(Regex("<[^>]*>"), "")
//                .replace("  ", " ")
//                .trim()
//        } ?: listOf("No directions available")
//    }
//
//    fun cleanup() {
//        geoApiContext.shutdown()
//    }
//}