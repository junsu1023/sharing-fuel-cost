package com.jskim.sharingfuelcost.di

import android.content.Context
import com.jskim.sharingfuelcost.BuildConfig
import com.jskim.sharingfuelcost.data.kakao.KakaoDataSource
import com.jskim.sharingfuelcost.data.location.AndroidCurrentLocationRepository
import com.jskim.sharingfuelcost.data.opinet.OpinetDataSource
import com.jskim.sharingfuelcost.data.vehicle.SharedPreferencesMyCarRepository
import com.jskim.sharingfuelcost.data.vehicle.VehicleDataSource
import com.jskim.sharingfuelcost.domain.fuel.FuelPriceRepository
import com.jskim.sharingfuelcost.domain.fuel.MyCarRepository
import com.jskim.sharingfuelcost.domain.fuel.VehicleRepository
import com.jskim.sharingfuelcost.domain.route.CurrentLocationRepository
import com.jskim.sharingfuelcost.domain.route.PlaceSearchRepository
import com.jskim.sharingfuelcost.domain.route.RouteDistanceRepository
import com.jskim.sharingfuelcost.domain.settlement.SettlementCalculator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
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
    fun provideCurrentLocationRepository(@ApplicationContext context: Context): CurrentLocationRepository =
        AndroidCurrentLocationRepository(context)

    @Provides
    @Singleton
    fun provideFuelPriceRepository(@ApplicationContext context: Context): FuelPriceRepository =
        OpinetDataSource(context, BuildConfig.OPINET_API_KEY).fuelPriceRepository

    @Provides
    @Singleton
    fun provideVehicleRepository(): VehicleRepository =
        VehicleDataSource(BuildConfig.DATA_GO_KR_API_KEY).vehicleRepository

    @Provides
    @Singleton
    fun provideMyCarRepository(@ApplicationContext context: Context): MyCarRepository =
        SharedPreferencesMyCarRepository(context)

    @Provides
    fun provideSettlementCalculator(): SettlementCalculator = SettlementCalculator()
}
