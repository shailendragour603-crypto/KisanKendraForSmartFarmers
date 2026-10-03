package shop.sabjiwala.kisankendra.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

object LocationHelper {

    private const val PREFS_LOCATION = "location_prefs"
    private const val KEY_DISTRICT = "saved_district"
    private const val KEY_STATE = "saved_state"

    fun getSavedDistrict(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_LOCATION, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DISTRICT, null)
    }

    fun getSavedState(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_LOCATION, Context.MODE_PRIVATE)
        return prefs.getString(KEY_STATE, null)
    }

    fun saveUserLocation(context: Context, district: String?, state: String?) {
        if (district.isNullOrBlank() && state.isNullOrBlank()) return
        val prefs = context.getSharedPreferences(PREFS_LOCATION, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_DISTRICT, district)
            putString(KEY_STATE, state)
            apply()
        }
    }

    fun clearSavedLocation(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_LOCATION, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    fun cleanDistrictName(rawName: String?): String? {
        if (rawName.isNullOrBlank()) return null
        return rawName
            .replace("(?i)subdivision|division|tehsil|taluk|block|district".toRegex(), "")
            .trim()
    }

    @SuppressLint("MissingPermission")
    fun getUserDistrict(context: Context, onResult: (districtName: String?, stateName: String?) -> Unit) {
        getUserLocationDetails(context) { district, state, _ ->
            onResult(district, state)
        }
    }

    @SuppressLint("MissingPermission")
    fun getUserLocationDetails(
        context: Context,
        onResult: (districtName: String?, stateName: String?, localityName: String?) -> Unit
    ) {
        val savedDistrict = getSavedDistrict(context)
        val savedState = getSavedState(context)

        try {
            val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
            if (availability != ConnectionResult.SUCCESS) {
                onResult(savedDistrict, savedState, null)
                return
            }

            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()

            // ⚡ FAST PATH: Check lastLocation first for instant (<1ms) response
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null) {
                    processLocation(context, lastLoc.latitude, lastLoc.longitude, onResult)
                } else {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                processLocation(context, location.latitude, location.longitude, onResult)
                            } else {
                                onResult(savedDistrict, savedState, null)
                            }
                        }
                        .addOnFailureListener {
                            onResult(savedDistrict, savedState, null)
                        }
                }
            }.addOnFailureListener {
                onResult(savedDistrict, savedState, null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(savedDistrict, savedState, null)
        }
    }

    private fun processLocation(
        context: Context,
        lat: Double,
        lng: Double,
        onResult: (districtName: String?, stateName: String?, localityName: String?) -> Unit
    ) {
        try {
            val geocoder = Geocoder(context, Locale.ENGLISH)
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val locality = cleanDistrictName(addr.locality)
                val subLocality = cleanDistrictName(addr.subLocality)
                val subAdmin = cleanDistrictName(addr.subAdminArea)
                val state = addr.adminArea

                // Prioritize locality (City/District) over subAdmin (Division)
                val bestDistrict = when {
                    !locality.isNullOrBlank() -> locality
                    !subLocality.isNullOrBlank() -> subLocality
                    !subAdmin.isNullOrBlank() -> subAdmin
                    else -> getSavedDistrict(context)
                }

                saveUserLocation(context, bestDistrict, state)
                onResult(bestDistrict, state, locality)
            } else {
                onResult(getSavedDistrict(context), getSavedState(context), null)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onResult(getSavedDistrict(context), getSavedState(context), null)
        }
    }
}
