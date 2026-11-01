package com.jskim.sharingfuelcost.domain.route

data class GeoPoint(
    val longitude: Double,
    val latitude: Double,
)

data class Place(
    val name: String,
    val address: String,
    val point: GeoPoint,
)

data class RouteDistances(
    val totalMeters: Long,
    /** 경유 순서대로, 각 탑승 위치부터 목적지까지의 거리 */
    val riderMeters: List<Long>,
)

enum class LookupFailure {
    NOT_CONFIGURED,
    NO_ROUTE,
    UNAUTHORIZED,
    QUOTA_EXCEEDED,
    NETWORK,
    UNEXPECTED,
}

sealed interface LookupResult<out T> {
    data class Success<T>(val value: T) : LookupResult<T>
    data class Failure(val reason: LookupFailure) : LookupResult<Nothing>
}

interface RouteDistanceRepository {
    suspend fun fetchDistances(
        origin: GeoPoint,
        destination: GeoPoint,
        pickups: List<GeoPoint>,
    ): LookupResult<RouteDistances>
}

interface PlaceSearchRepository {
    suspend fun search(query: String): LookupResult<List<Place>>
}
