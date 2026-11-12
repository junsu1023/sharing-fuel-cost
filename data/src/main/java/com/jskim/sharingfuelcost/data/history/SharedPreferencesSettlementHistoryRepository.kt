package com.jskim.sharingfuelcost.data.history

import android.content.Context
import com.jskim.sharingfuelcost.domain.fuel.FuelType
import com.jskim.sharingfuelcost.domain.history.SettlementHistoryRepository
import com.jskim.sharingfuelcost.domain.history.SettlementRecord
import com.jskim.sharingfuelcost.domain.history.addSettlementRecord
import com.jskim.sharingfuelcost.domain.settlement.ParticipantShare
import com.jskim.sharingfuelcost.domain.settlement.SettlementMethod
import com.jskim.sharingfuelcost.domain.settlement.SettlementResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

/** 기록은 최대 50건이라 목록 전체를 JSON 하나로 저장한다. */
class SharedPreferencesSettlementHistoryRepository(context: Context) : SettlementHistoryRepository {
    private val preferences =
        context.applicationContext.getSharedPreferences("settlement_history", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun records(): List<SettlementRecord> = mutex.withLock { read() }

    override suspend fun add(record: SettlementRecord) = mutex.withLock {
        write(addSettlementRecord(read(), record))
    }

    override suspend fun delete(createdAtMillis: Long) = mutex.withLock {
        write(read().filterNot { it.createdAtMillis == createdAtMillis })
    }

    private suspend fun read() = withContext(Dispatchers.IO) {
        preferences.getString(KEY_RECORDS, null)?.let(::decodeRecords).orEmpty()
    }

    private suspend fun write(records: List<SettlementRecord>) = withContext(Dispatchers.IO) {
        preferences.edit().putString(KEY_RECORDS, encodeRecords(records)).apply()
    }

    private companion object {
        const val KEY_RECORDS = "records"
    }
}

@Serializable
private data class RecordDto(
    val createdAtMillis: Long,
    val places: List<String>,
    val kmPerLiter: String,
    val wonPerLiter: String,
    val totalDistanceMeters: Long,
    val fuelLiters: String,
    val totalCostWon: Long,
    val method: String,
    val shares: List<ShareDto>,
    // 이 값이 생기기 전의 기록은 휘발유로 읽는다.
    val fuelType: String = "GASOLINE",
)

@Serializable
private data class ShareDto(
    val name: String? = null,
    val distanceMeters: Long,
    val amountWon: Long,
)

internal fun encodeRecords(records: List<SettlementRecord>): String = json.encodeToString(
    records.map { record ->
        val result = record.result
        RecordDto(
            createdAtMillis = record.createdAtMillis,
            places = record.places,
            kmPerLiter = record.kmPerLiter.toPlainString(),
            wonPerLiter = record.wonPerLiter.toPlainString(),
            totalDistanceMeters = result.totalDistanceMeters,
            fuelLiters = result.fuelLiters.toPlainString(),
            totalCostWon = result.totalCostWon,
            method = result.method.name,
            shares = result.shares.map { ShareDto(it.name, it.distanceMeters, it.amountWon) },
            fuelType = record.fuelType.name,
        )
    },
)

/** 저장 형식이 깨졌으면 기록이 없는 것으로 보고, 읽을 수 없는 기록 한 건은 건너뛴다. */
internal fun decodeRecords(value: String): List<SettlementRecord> {
    val dtos = runCatching { json.decodeFromString<List<RecordDto>>(value) }.getOrNull() ?: return emptyList()
    return dtos.mapNotNull { dto ->
        runCatching {
            SettlementRecord(
                createdAtMillis = dto.createdAtMillis,
                places = dto.places,
                kmPerLiter = dto.kmPerLiter.toBigDecimal(),
                wonPerLiter = dto.wonPerLiter.toBigDecimal(),
                result = SettlementResult(
                    totalDistanceMeters = dto.totalDistanceMeters,
                    fuelLiters = dto.fuelLiters.toBigDecimal(),
                    totalCostWon = dto.totalCostWon,
                    method = SettlementMethod.valueOf(dto.method),
                    shares = dto.shares.map { ParticipantShare(it.name, it.distanceMeters, it.amountWon) },
                ),
                fuelType = FuelType.valueOf(dto.fuelType),
            )
        }.getOrNull()
    }
}
