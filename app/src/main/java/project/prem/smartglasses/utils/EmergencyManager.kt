package project.prem.smartglasses.utils

import android.content.Context
import android.location.Location
import android.telephony.SmsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EmergencyManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val locationManager = LocationManager(context)
    private val smsManager: SmsManager = SmsManager.getDefault()

    // In a real app, this would be stored securely
    private val emergencyContact = "+91-7020791879"

    fun sendEmergencyAlert() {
        scope.launch {
            val location = locationManager.getCurrentLocation()
                val message = createEmergencyMessage(location)
                sendSMS(message)

        }
    }

    private fun createEmergencyMessage(location: Location?): String {
        return "EMERGENCY: Assistance needed at this location: " +
                "https://www.google.com/maps?q=${location?.latitude},${location?.longitude}"
    }

    private fun sendSMS(message: String) {
        try {
            smsManager.sendTextMessage(
                emergencyContact,
                null,
                message,
                null,
                null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}