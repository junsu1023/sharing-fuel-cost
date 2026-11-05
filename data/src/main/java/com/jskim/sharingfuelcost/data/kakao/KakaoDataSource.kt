package com.jskim.sharingfuelcost.data.kakao

import com.jskim.sharingfuelcost.data.relay.RelayClient
import com.jskim.sharingfuelcost.domain.route.PlaceSearchRepository
import com.jskim.sharingfuelcost.domain.route.RouteDistanceRepository
import retrofit2.create

/** 카카오 REST API 구현을 만든다. 키는 중계 서버가 붙이고, 중계 주소가 없으면 NOT_CONFIGURED로 실패한다. */
class KakaoDataSource(relay: RelayClient) {

    val routeDistanceRepository: RouteDistanceRepository = KakaoRouteDistanceRepository(
        relay.retrofit("kakao-navi").create(),
        relay.isConfigured,
    )

    val placeSearchRepository: PlaceSearchRepository = KakaoPlaceSearchRepository(
        relay.retrofit("kakao-local").create(),
        relay.isConfigured,
    )
}
