package com.fitghost.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Simple location provider that:
 * - Requests runtime location permission (FINE/COARSE)
 * - Retrieves the last known location from Android SDK [LocationManager]
 *
 * Notes:
 * - Caller is responsible for handling the permission result in the Activity's
 * onRequestPermissionsResult (or ActivityResult API).
 * - If permission is not granted, location methods return null.
 * - No external Play Services dependency required.
 */
object LocationProvider {

    const val DEFAULT_PERMISSION_REQUEST_CODE: Int = 1001

    private val LOCATION_PERMISSIONS =
            arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            )

    fun hasLocationPermission(context: Context): Boolean {
        val fine =
                ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        val coarse =
                ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Returns true if permission is already granted. If not granted, triggers a permission request
     * and returns false.
     */
    fun ensureLocationPermission(
            activity: Activity,
            requestCode: Int = DEFAULT_PERMISSION_REQUEST_CODE
    ): Boolean {
        return if (hasLocationPermission(activity)) {
            true
        } else {
            ActivityCompat.requestPermissions(activity, LOCATION_PERMISSIONS, requestCode)
            false
        }
    }

    /**
     * Request location permission explicitly (without checking). Prefer [ensureLocationPermission]
     * for convenience.
     */
    fun requestLocationPermission(
            activity: Activity,
            requestCode: Int = DEFAULT_PERMISSION_REQUEST_CODE
    ) {
        ActivityCompat.requestPermissions(activity, LOCATION_PERMISSIONS, requestCode)
    }

    private fun locationManager(context: Context): LocationManager {
        return context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    @SuppressLint("MissingPermission")
    private fun bestLastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = locationManager(context)
        val providers =
                listOf(
                        LocationManager.GPS_PROVIDER,
                        LocationManager.NETWORK_PROVIDER,
                        LocationManager.PASSIVE_PROVIDER
                )

        var best: Location? = null
        for (p in providers) {
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || loc.time > best!!.time) {
                best = loc
            }
        }
        return best
    }

    /**
     * Retrieves the last known location asynchronously (suspend). Returns null if:
     * - Permission is not granted
     * - No last known location exists
     * - Any error occurs
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        // For consistency with previous API, keep suspend signature even though this is immediate.
        return suspendCancellableCoroutine { cont ->
            val loc = bestLastKnownLocation(context)
            if (!cont.isCompleted) cont.resume(loc)
        }
    }

    /**
     * Retrieves the last known location with a simple callback API. Returns null in the callback
     * if:
     * - Permission is not granted
     * - No last known location exists
     * - Any error occurs
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(context: Context, onResult: (Location?) -> Unit) {
        if (!hasLocationPermission(context)) {
            onResult(null)
            return
        }
        onResult(bestLastKnownLocation(context))
    }
}
