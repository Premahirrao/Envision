package project.prem.smartglasses.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class LocationManager(private val context: Context) {
    private val TAG = "LocationManager"
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation

    private val locationManager: LocationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            Log.d(TAG, "Location updated: ${location.latitude}, ${location.longitude}")
            _currentLocation.value = location
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

        @Deprecated("Deprecated in Java")
        override fun onProviderEnabled(provider: String) {
            Log.d(TAG, "Provider enabled: $provider")
        }

        @Deprecated("Deprecated in Java")
        override fun onProviderDisabled(provider: String) {
            Log.d(TAG, "Provider disabled: $provider")
        }
    }

    init {
        Log.d(TAG, "Initializing LocationManager")

        // Set default location (New York City) if location permissions not granted
        // This ensures the app works even without location permissions
        val defaultLocation = Location("default").apply {
            latitude = 40.7128
            longitude = -74.0060
        }
        _currentLocation.value = defaultLocation

        // Try to get last known location immediately
        getCurrentLocation()

        // Start location updates if permissions are granted
        startLocationUpdates()
    }

    fun getCurrentLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Location permission not granted")
            return _currentLocation.value
        }

        try {
            val gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (gpsLocation != null) {
                Log.d(TAG, "Got GPS location: ${gpsLocation.latitude}, ${gpsLocation.longitude}")
                _currentLocation.value = gpsLocation
                return gpsLocation
            }

            val networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (networkLocation != null) {
                Log.d(TAG, "Got network location: ${networkLocation.latitude}, ${networkLocation.longitude}")
                _currentLocation.value = networkLocation
                return networkLocation
            }

            Log.d(TAG, "No location available, returning current value: ${_currentLocation.value}")
            return _currentLocation.value
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            return _currentLocation.value
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Location permission not granted, can't start updates")
            return
        }

        try {
            Log.d(TAG, "Starting location updates")
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000, // Update every 5 seconds
                10f,  // Or when moved 10 meters
                locationListener
            )

            // Also request network provider updates as backup
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                5000,
                10f,
                locationListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error starting location updates", e)
        }
    }

    fun stopLocationUpdates() {
        try {
            Log.d(TAG, "Stopping location updates")
            locationManager.removeUpdates(locationListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping location updates", e)
        }
    }

    fun updateLocation(location: Location) {
        Log.d(TAG, "Manually updating location: ${location.latitude}, ${location.longitude}")
        _currentLocation.value = location
    }
}

//import android.Manifest
//import android.content.Context
//import android.content.pm.PackageManager
//import android.location.Location
//import androidx.core.content.ContextCompat
//import com.google.android.gms.location.*
//import com.google.android.gms.maps.model.LatLng
//import kotlinx.coroutines.tasks.await
//
//class LocationManager(private val context: Context) {
//    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
//
//    suspend fun getCurrentLocation(): LatLng {
//        return try {
//            if (!hasLocationPermission()) {
//                LatLng(0.0, 0.0)
//            } else {
//                val location = fusedLocationClient.lastLocation.await()
//
//                location?.let {
//                    LatLng(it.latitude, it.longitude)
//                } ?: LatLng(0.0, 0.0)
//            }
//        } catch (e: SecurityException) {
//            e.printStackTrace()
//            LatLng(0.0, 0.0)
//        } catch (e: Exception) {
//            e.printStackTrace()
//            LatLng(0.0, 0.0)
//        }
//    }
//
//    private fun hasLocationPermission(): Boolean {
//        return ContextCompat.checkSelfPermission(
//            context,
//            Manifest.permission.ACCESS_FINE_LOCATION
//        ) == PackageManager.PERMISSION_GRANTED ||
//                ContextCompat.checkSelfPermission(
//                    context,
//                    Manifest.permission.ACCESS_COARSE_LOCATION
//                ) == PackageManager.PERMISSION_GRANTED
//    }
////}
//import android.annotation.SuppressLint
//import android.content.Context
//import android.location.Location
//import com.google.android.gms.location.LocationServices
//import kotlinx.coroutines.tasks.await
//
//class LocationManager(private val context: Context) {
//    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
//
//    @SuppressLint("MissingPermission")
//    suspend fun getCurrentLocation(): Location? {
//        return try {
//            fusedLocationClient.lastLocation.await()
//        } catch (e: Exception) {
//            null
//        }
//    }
//}