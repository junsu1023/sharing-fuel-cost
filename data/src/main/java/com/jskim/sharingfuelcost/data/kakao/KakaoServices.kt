package com.jskim.sharingfuelcost.data.kakao

import retrofit2.http.GET
import retrofit2.http.Query

internal interface KakaoDirectionsService {
    @GET("v1/directions")
    suspend fun directions(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("waypoints") waypoints: String?,
    ): DirectionsResponse
}

internal interface KakaoLocalService {
    @GET("v2/local/search/address.json")
    suspend fun searchAddress(@Query("query") query: String): AddressSearchResponse

    @GET("v2/local/search/keyword.json")
    suspend fun searchKeyword(@Query("query") query: String): KeywordSearchResponse
}
