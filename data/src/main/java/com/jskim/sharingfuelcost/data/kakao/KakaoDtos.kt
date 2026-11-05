package com.jskim.sharingfuelcost.data.kakao

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class DirectionsResponse(val routes: List<RouteDto> = emptyList())

@Serializable
internal data class RouteDto(
    @SerialName("result_code") val resultCode: Int,
    val summary: SummaryDto? = null,
    val sections: List<SectionDto> = emptyList(),
)

/** distance 단위는 미터다. */
@Serializable
internal data class SummaryDto(val distance: Long)

/** distance 단위는 미터다. */
@Serializable
internal data class SectionDto(val distance: Long)

@Serializable
internal data class AddressSearchResponse(val documents: List<AddressDocument> = emptyList())

@Serializable
internal data class AddressDocument(
    @SerialName("address_name") val addressName: String,
    val x: String,
    val y: String,
)

@Serializable
internal data class CoordToAddressResponse(val documents: List<CoordToAddressDocument> = emptyList())

@Serializable
internal data class CoordToAddressDocument(
    val address: LotAddressDto? = null,
    @SerialName("road_address") val roadAddress: RoadAddressDto? = null,
)

@Serializable
internal data class LotAddressDto(@SerialName("address_name") val addressName: String = "")

@Serializable
internal data class RoadAddressDto(
    @SerialName("address_name") val addressName: String = "",
    @SerialName("building_name") val buildingName: String = "",
)

@Serializable
internal data class KeywordSearchResponse(val documents: List<KeywordDocument> = emptyList())

@Serializable
internal data class KeywordDocument(
    @SerialName("place_name") val placeName: String,
    @SerialName("address_name") val addressName: String = "",
    @SerialName("road_address_name") val roadAddressName: String = "",
    val x: String,
    val y: String,
)
