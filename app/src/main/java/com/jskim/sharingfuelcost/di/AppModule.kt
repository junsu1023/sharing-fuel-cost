package com.jskim.sharingfuelcost.di

import android.content.Context
import com.google.firebase.FirebaseApp
import com.jskim.sharingfuelcost.BuildConfig
import com.jskim.sharingfuelcost.currentAppCheckToken
import com.jskim.sharingfuelcost.data.history.SharedPreferencesSettlementHistoryRepository
import com.jskim.sharingfuelcost.data.kakao.KakaoDataSource
import com.jskim.sharingfuelcost.data.location.AndroidCurrentLocationRepository
import com.jskim.sharingfuelcost.data.opinet.OpinetDataSource
import com.jskim.sharingfuelcost.data.relay.RelayClient
import com.jskim.sharingfuelcost.data.vehicle.SharedPreferencesMyCarRepository
import com.jskim.sharingfuelcost.data.vehicle.VehicleDataSource
import com.jskim.sharingfuelcost.domain.fuel.FuelPriceRepository
import com.jskim.sharingfuelcost.domain.fuel.MyCarRepository
import com.jskim.sharingfuelcost.domain.fuel.VehicleRepository
import com.jskim.sharingfuelcost.domain.history.SettlementHistoryRepository
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

    /** Firebase가 켜져 있을 때만 App Check 토큰을 붙인다. 없으면 중계 서버가 401로 거절한다. */
    @Provides
    @Singleton
    fun provideRelayClient(@ApplicationContext context: Context): RelayClient {
        val firebaseReady = FirebaseApp.getApps(context).isNotEmpty()
        return RelayClient(BuildConfig.RELAY_BASE_URL) { if (firebaseReady) currentAppCheckToken() else null }
    }

    @Provides
    @Singleton
    fun provideKakaoDataSource(relay: RelayClient): KakaoDataSource = KakaoDataSource(relay)

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
    fun provideFuelPriceRepository(@ApplicationContext context: Context, relay: RelayClient): FuelPriceRepository =
        OpinetDataSource(context, relay).fuelPriceRepository

    @Provides
    @Singleton
    fun provideVehicleRepository(relay: RelayClient): VehicleRepository = VehicleDataSource(relay).vehicleRepository

    @Provides
    @Singleton
    fun provideMyCarRepository(@ApplicationContext context: Context): MyCarRepository =
        SharedPreferencesMyCarRepository(context)

    @Provides
    @Singleton
    fun provideSettlementHistoryRepository(@ApplicationContext context: Context): SettlementHistoryRepository =
        SharedPreferencesSettlementHistoryRepository(context)

    @Provides
    fun provideSettlementCalculator(): SettlementCalculator = SettlementCalculator()
}
