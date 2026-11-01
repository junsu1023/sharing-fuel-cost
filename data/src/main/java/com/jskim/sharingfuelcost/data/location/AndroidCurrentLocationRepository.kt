package com.jskim.sharingfuelcost.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.jskim.sharingfuelcost.domain.route.CurrentLocationRepository
import com.jskim.sharingfuelcost.domain.route.GeoPoint
import com.jskim.sharingfuelcost.domain.route.LookupFailure
import com.jskim.sharingfuelcost.domain.route.LookupResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val LOCATION_TIMEOUT_MILLIS = 10_000L

/** 권한은 presentation에서 받는다. 권한이 없으면 SecurityException을 실패로 바꾼다. */
class AndroidCurrentLocationRepository(context: Context) : CurrentLocationRepository {

    private val appContext = context.applicationContext
    private val locationManager = appContext.getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    override suspend fun currentPoint(): LookupResult<GeoPoint> {
        val unavailable = LookupResult.Failure(LookupFailure.LOCATION_UNAVAILABLE)
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) return unavailable
        return try {
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .filter { locationManager.isProviderEnabled(it) }
            val location = providers.firstNotNullOfOrNull { requestLocation(it) }
                ?: providers.firstNotNullOfOrNull { locationManager.getLastKnownLocation(it) }
            location?.let { LookupResult.Success(GeoPoint(it.longitude, it.latitude)) } ?: unavailable
        } catch (_: SecurityException) {
            unavailable
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(provider: String): Location? =
        withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    locationManager,
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(appContext),
                ) { location -> if (continuation.isActive) continuation.resume(location) }
            }
        }
}
