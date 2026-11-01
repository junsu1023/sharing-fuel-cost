package com.jskim.sharingfuelcost.data.kakao

import com.jskim.sharingfuelcost.domain.route.GeoPoint
import com.jskim.sharingfuelcost.domain.route.LookupFailure
import com.jskim.sharingfuelcost.domain.route.LookupResult
import com.jskim.sharingfuelcost.domain.route.Place
import com.jskim.sharingfuelcost.domain.route.PlaceSearchRepository
import com.jskim.sharingfuelcost.domain.route.RouteDistanceRepository
import com.jskim.sharingfuelcost.domain.route.RouteDistances
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

internal class KakaoRouteDistanceRepository(
    private val service: KakaoDirectionsService,
    private val isConfigured: Boolean,
) : RouteDistanceRepository {

    override suspend fun fetchDistances(
        origin: GeoPoint,
        destination: GeoPoint,
        pickups: List<GeoPoint>,
    ): LookupResult<RouteDistances> {
        if (!isConfigured) return LookupResult.Failure(LookupFailure.NOT_CONFIGURED)
        return safeLookup {
            val response = service.directions(
                origin = origin.toQuery(),
                destination = destination.toQuery(),
                waypoints = pickups.takeIf { it.isNotEmpty() }?.joinToString("|") { it.toQuery() },
            )
            response.toRouteDistances(pickups.size)
        }
    }
}

internal class KakaoPlaceSearchRepository(
    private val service: KakaoLocalService,
    private val isConfigured: Boolean,
) : PlaceSearchRepository {

    override suspend fun search(query: String): LookupResult<List<Place>> {
        if (!isConfigured) return LookupResult.Failure(LookupFailure.NOT_CONFIGURED)
        return safeLookup {
            val addresses = service.searchAddress(query).documents.mapNotNull { it.toPlace() }
            val keywords = service.searchKeyword(query).documents.mapNotNull { it.toPlace() }
            LookupResult.Success((addresses + keywords).distinctBy { it.name to it.address })
        }
    }
}

internal fun DirectionsResponse.toRouteDistances(pickupCount: Int): LookupResult<RouteDistances> {
    val route = routes.firstOrNull() ?: return LookupResult.Failure(LookupFailure.UNEXPECTED)
    if (route.resultCode != 0) return LookupResult.Failure(LookupFailure.NO_ROUTE)
    val summary = route.summary ?: return LookupResult.Failure(LookupFailure.UNEXPECTED)
    if (route.sections.size != pickupCount + 1) return LookupResult.Failure(LookupFailure.UNEXPECTED)

    // i번째 탑승 위치 다음 구간부터 마지막 구간까지가 그 탑승자의 이동 거리다.
    val riderMeters = List(pickupCount) { index ->
        route.sections.drop(index + 1).sumOf { it.distance }
    }
    return LookupResult.Success(RouteDistances(summary.distance, riderMeters))
}

internal fun AddressDocument.toPlace(): Place? =
    point(x, y)?.let { Place(name = addressName, address = addressName, point = it) }

internal fun KeywordDocument.toPlace(): Place? =
    point(x, y)?.let { Place(name = placeName, address = roadAddressName.ifBlank { addressName }, point = it) }

private fun point(x: String, y: String): GeoPoint? {
    val longitude = x.toDoubleOrNull() ?: return null
    val latitude = y.toDoubleOrNull() ?: return null
    return GeoPoint(longitude, latitude)
}

private fun GeoPoint.toQuery(): String = "$longitude,$latitude"

internal inline fun <T> safeLookup(block: () -> LookupResult<T>): LookupResult<T> =
    try {
        block()
    } catch (e: HttpException) {
        LookupResult.Failure(
            when (e.code()) {
                401, 403 -> LookupFailure.UNAUTHORIZED
                429 -> LookupFailure.QUOTA_EXCEEDED
                else -> LookupFailure.UNEXPECTED
            },
        )
    } catch (_: IOException) {
        LookupResult.Failure(LookupFailure.NETWORK)
    } catch (_: SerializationException) {
        LookupResult.Failure(LookupFailure.UNEXPECTED)
    }
