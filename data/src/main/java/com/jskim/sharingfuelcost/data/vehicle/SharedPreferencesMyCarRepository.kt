package com.jskim.sharingfuelcost.data.vehicle

import android.content.Context
import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.fuel.MyCar
import com.jskim.sharingfuelcost.domain.fuel.MyCarRepository
import com.jskim.sharingfuelcost.domain.fuel.VehicleFuelEconomy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

class SharedPreferencesMyCarRepository(context: Context) : MyCarRepository {
    private val preferences = context.applicationContext.getSharedPreferences("my_car", Context.MODE_PRIVATE)

    override suspend fun load(): MyCar? = withContext(Dispatchers.IO) {
        preferences.getString(KEY_CAR, null)?.let(::decodeMyCar)
    }

    override suspend fun save(car: MyCar) = withContext(Dispatchers.IO) {
        preferences.edit().putString(KEY_CAR, encodeMyCar(car)).apply()
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        preferences.edit().remove(KEY_CAR).apply()
    }

    private companion object {
        const val KEY_CAR = "car"
    }
}

@Serializable
private data class MyCarDto(
    val kmPerLiter: String,
    val fuelType: String,
    val vehicle: VehicleDto? = null,
)

@Serializable
private data class VehicleDto(
    val maker: String,
    val model: String,
    val year: String,
    val fuelName: String,
    val fuelType: String,
    val kmPerLiter: String,
)

internal fun encodeMyCar(car: MyCar): String = json.encodeToString(
    MyCarDto(
        kmPerLiter = car.kmPerLiter.toPlainString(),
        fuelType = car.fuelType.name,
        vehicle = car.vehicle?.let {
            VehicleDto(it.maker, it.model, it.year, it.fuelName, it.fuelType.name, it.kmPerLiter.toPlainString())
        },
    ),
)

/** 저장 형식이 깨졌거나 알 수 없는 값이면 저장된 차가 없는 것으로 본다. */
internal fun decodeMyCar(value: String): MyCar? = runCatching {
    val dto = json.decodeFromString<MyCarDto>(value)
    MyCar(
        vehicle = dto.vehicle?.let {
            VehicleFuelEconomy(
                it.maker,
                it.model,
                it.year,
                it.fuelName,
                FuelType.valueOf(it.fuelType),
                it.kmPerLiter.toBigDecimal(),
            )
        },
        kmPerLiter = dto.kmPerLiter.toBigDecimal(),
        fuelType = FuelType.valueOf(dto.fuelType),
    )
}.getOrNull()
