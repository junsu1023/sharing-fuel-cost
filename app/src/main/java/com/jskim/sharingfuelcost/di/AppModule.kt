package com.jskim.sharingfuelcost.di

import com.jskim.sharingfuelcost.BuildConfig
import com.jskim.sharingfuelcost.data.kakao.KakaoDataSource
import com.jskim.sharingfuelcost.domain.route.PlaceSearchRepository
import com.jskim.sharingfuelcost.domain.route.RouteDistanceRepository
import com.jskim.sharingfuelcost.domain.settlement.SettlementCalculator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideKakaoDataSource(): KakaoDataSource = KakaoDataSource(BuildConfig.KAKAO_REST_API_KEY)

    @Provides
    fun provideRouteDistanceRepository(source: KakaoDataSource): RouteDistanceRepository =
        source.routeDistanceRepository

    @Provides
    fun providePlaceSearchRepository(source: KakaoDataSource): PlaceSearchRepository =
        source.placeSearchRepository

    @Provides
    fun provideSettlementCalculator(): SettlementCalculator = SettlementCalculator()
}
