package com.jskim.sharingfuelcost.data.kakao

import com.jskim.sharingfuelcost.domain.route.GeoPoint
import com.jskim.sharingfuelcost.domain.route.LookupFailure
import com.jskim.sharingfuelcost.domain.route.LookupResult
import com.jskim.sharingfuelcost.domain.route.RouteDistances
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class KakaoRepositoriesTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val a = GeoPoint(127.0, 37.0)
    private val b = GeoPoint(128.0, 38.0)
    private val pickup1 = GeoPoint(127.1, 37.1)
    private val pickup2 = GeoPoint(127.2, 37.2)

    private fun repository(
        configured: Boolean = true,
        answer: () -> DirectionsResponse,
    ) = KakaoRouteDistanceRepository(
        object : KakaoDirectionsService {
            override suspend fun directions(origin: String, destination: String, waypoints: String?) = answer()
        },
        configured,
    )

    private fun httpError(code: Int) =
        HttpException(Response.error<Any>(code, "{}".toResponseBody()))

    private fun failureOf(answer: () -> DirectionsResponse) = runBlocking {
        repository(answer = answer).fetchDistances(a, b, emptyList())
    }

    @Test
    fun `parses response and sums sections after each pickup`() {
        val response = json.decodeFromString<DirectionsResponse>(
            """{"trans_id":"t","routes":[{"result_code":0,"result_msg":"길찾기 성공",
            "summary":{"distance":256734,"duration":1},
            "sections":[{"distance":6418},{"distance":28778},{"distance":221538}]}]}""",
        )
        assertEquals(
            LookupResult.Success(RouteDistances(256_734, listOf(250_316, 221_538))),
            response.toRouteDistances(2),
        )
    }

    @Test
    fun `no pickups gives only total`() {
        val response = DirectionsResponse(listOf(RouteDto(0, SummaryDto(100), listOf(SectionDto(100)))))
        assertEquals(LookupResult.Success(RouteDistances(100, emptyList())), response.toRouteDistances(0))
    }

    @Test
    fun `non zero result code is no route`() {
        val response = json.decodeFromString<DirectionsResponse>("""{"routes":[{"result_code":104}]}""")
        assertEquals(LookupResult.Failure(LookupFailure.NO_ROUTE), response.toRouteDistances(0))
    }

    @Test
    fun `section count mismatch is unexpected`() {
        val response = DirectionsResponse(listOf(RouteDto(0, SummaryDto(100), listOf(SectionDto(100)))))
        assertEquals(LookupResult.Failure(LookupFailure.UNEXPECTED), response.toRouteDistances(2))
    }

    @Test
    fun `http and network errors map to failures`() {
        assertEquals(LookupResult.Failure(LookupFailure.UNAUTHORIZED), failureOf { throw httpError(401) })
        assertEquals(LookupResult.Failure(LookupFailure.UNAUTHORIZED), failureOf { throw httpError(403) })
        assertEquals(LookupResult.Failure(LookupFailure.QUOTA_EXCEEDED), failureOf { throw httpError(429) })
        assertEquals(LookupResult.Failure(LookupFailure.UNEXPECTED), failureOf { throw httpError(500) })
        assertEquals(LookupResult.Failure(LookupFailure.NETWORK), failureOf { throw IOException() })
    }

    @Test
    fun `missing key fails without calling api`() {
        val result = runBlocking {
            repository(configured = false) { error("must not call") }.fetchDistances(a, b, listOf(pickup1, pickup2))
        }
        assertEquals(LookupResult.Failure(LookupFailure.NOT_CONFIGURED), result)
    }

    @Test
    fun `place search reports missing key and http failures`() {
        val service = object : KakaoLocalService {
            override suspend fun searchAddress(query: String): AddressSearchResponse = throw httpError(429)
            override suspend fun searchKeyword(query: String) = KeywordSearchResponse()
            override suspend fun coordToAddress(x: String, y: String): CoordToAddressResponse = throw httpError(401)
        }
        val unconfigured = KakaoPlaceSearchRepository(service, false)
        val configured = KakaoPlaceSearchRepository(service, true)
        runBlocking {
            assertEquals(LookupResult.Failure(LookupFailure.NOT_CONFIGURED), unconfigured.search("q"))
            assertEquals(LookupResult.Failure(LookupFailure.NOT_CONFIGURED), unconfigured.reverseGeocode(a))
            assertEquals(LookupResult.Failure(LookupFailure.QUOTA_EXCEEDED), configured.search("q"))
            assertEquals(LookupResult.Failure(LookupFailure.UNAUTHORIZED), configured.reverseGeocode(a))
        }
    }

    @Test
    fun `reverse geocode prefers building then road then lot address`() {
        val response = json.decodeFromString<CoordToAddressResponse>(
            """{"meta":{"total_count":1},"documents":[{"road_address":{"address_name":"도로명","building_name":"건물"},
            "address":{"address_name":"지번"}}]}""",
        )
        val place = response.documents.single().toPlace(a)
        assertEquals("건물", place?.name)
        assertEquals("도로명", place?.address)

        val lotOnly = CoordToAddressDocument(address = LotAddressDto("지번"), roadAddress = null).toPlace(a)
        assertEquals("지번", lotOnly?.name)
        assertEquals(null, CoordToAddressDocument().toPlace(a))
    }

    @Test
    fun `search merges address and keyword and drops invalid coordinates`() {
        val service = object : KakaoLocalService {
            override suspend fun searchAddress(query: String) =
                AddressSearchResponse(listOf(AddressDocument("서울 주소", "127.0", "37.0")))

            override suspend fun searchKeyword(query: String) = KeywordSearchResponse(
                listOf(
                    KeywordDocument("강남역", "지번", "도로명", "127.1", "37.1"),
                    KeywordDocument("좌표오류", x = "", y = ""),
                ),
            )

            override suspend fun coordToAddress(x: String, y: String) = CoordToAddressResponse()
        }
        val result = runBlocking { KakaoPlaceSearchRepository(service, true).search("q") }
        val places = (result as LookupResult.Success).value
        assertEquals(listOf("서울 주소", "강남역"), places.map { it.name })
        assertEquals("도로명", places[1].address)
    }
}
