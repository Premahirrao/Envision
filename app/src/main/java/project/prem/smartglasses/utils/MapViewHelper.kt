package project.prem.smartglasses.utils

import android.content.Context
import android.location.Location
import android.util.Log
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.Road
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Polyline
import java.util.ArrayList

class OSMMapHelper(private val context: Context) {
    private val TAG = "OSMMapHelper"

    // This will store the polyline points for navigation
    private var roadOverlay: Polyline? = null
    private var routeBounds: BoundingBox? = null
    private var currentRoad: Road? = null


    init {
        // Initialize OSMDroid configuration
        Configuration.getInstance().load(context, context.getSharedPreferences("osm_prefs", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = "Navigator"
    }

    suspend fun getRoutePolyline(from: Location?, destination: GeoPoint): Polyline? {
        try {
            val fromLocation = from ?: return null

            Log.d(TAG, "Getting route polyline from ${fromLocation.latitude},${fromLocation.longitude} to $destination")

            // Create road manager for routing
            val roadManager = OSRMRoadManager(context, "Navigator")

            // Create waypoints
            val waypoints = ArrayList<GeoPoint>()
            waypoints.add(GeoPoint(fromLocation.latitude, fromLocation.longitude))
            waypoints.add(destination)

            try {
                // Calculate the route with timeout
                val road = roadManager.getRoad(waypoints)
                currentRoad = road
                Log.d(TAG, "Route calculation complete. Status: ${road.mStatus}")

                if (road.mStatus != Road.STATUS_OK) {
                    Log.e(TAG, "Error calculating route: ${road.mStatus}")
                    return null
                }

                // Log the number of nodes in the route
                Log.d(TAG, "Route has ${road.mNodes.size} nodes")

                // Create the polyline from the route
                roadOverlay = RoadManager.buildRoadOverlay(road)

                // Log the number of points in the polyline
                Log.d(TAG, "Road overlay created with ${roadOverlay?.actualPoints?.size ?: 0} points")

                // Set the route bounds
                if (road.mBoundingBox != null) {
                    routeBounds = road.mBoundingBox
                    Log.d(TAG, "Route bounds: $routeBounds")
                }

                return roadOverlay
            } catch (e: Exception) {
                Log.e(TAG, "Exception during route calculation", e)
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting route polyline", e)
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            return null
        }
    }

    fun getRouteBounds(): BoundingBox? {
        return routeBounds
    }

    fun getCurrentRoad(): Road? {
        return currentRoad
    }

    fun getRoutingInstructions(): List<String> {
        val instructions = mutableListOf<String>()
        try {
            // Check if currentRoad is null
            val road = currentRoad
            if (road == null) {
                Log.e(TAG, "Cannot get routing instructions: currentRoad is null")
                instructions.add("Start navigation")
                instructions.add("Continue to your destination")
                return instructions
            }

            // Check if road nodes are empty

            if (road.mNodes.isEmpty()) {
                Log.e(TAG, "Cannot get routing instructions: road nodes are empty")
                instructions.add("Start navigation")
                instructions.add("Continue to your destination")
                return instructions
            }

            // Process each navigation node to create detailed instructions
            for (i in 0 until road.mNodes.size) {
                val node = road.mNodes[i]

                // Safely get instruction and distance
                val nodeInstruction = node.mInstructions ?: ""
                val distance = node.mLength

                // Format distance in a user-friendly way
                val formattedDistance = when {
                    distance >= 1.0 -> String.format("%.1f kilometers", distance)
                    else -> String.format("%d meters", (distance * 1000).toInt())
                }

                // Create detailed instruction with distance
                val detailedInstruction = if (nodeInstruction.isNotEmpty() && !nodeInstruction.contains("have reached")) {
                    // Add distance information if not already in the instruction
                    if (!nodeInstruction.contains("meters") && !nodeInstruction.contains("kilometer")) {
                        "$nodeInstruction in $formattedDistance"
                    } else {
                        nodeInstruction
                    }
                } else if (i < road.mNodes.size - 1) {
                    "Continue straight for $formattedDistance"
                } else {
                    "You have arrived at your destination"
                }

                // Log for debugging
                Log.d(TAG, "Node $i instruction: $detailedInstruction")

                instructions.add(detailedInstruction)
            }

            // Always ensure there's a final destination instruction
            if (instructions.isEmpty() || (!instructions.last().contains("arrived") && !instructions.last().contains("destination"))) {
                instructions.add("You have arrived at your destination")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting routing instructions", e)

            // Add fallback instructions
            instructions.clear()
            instructions.add("Start navigation")
            instructions.add("Continue to your destination")
        }

        return instructions
    }
}
