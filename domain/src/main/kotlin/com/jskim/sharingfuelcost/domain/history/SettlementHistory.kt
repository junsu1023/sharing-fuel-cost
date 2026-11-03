package com.jskim.sharingfuelcost.domain.history

import com.jskim.sharingfuelcost.domain.settlement.SettlementResult
import java.math.BigDecimal

const val MAX_SETTLEMENT_RECORDS = 50

data class SettlementRecord(
    /** 저장한 시각(epoch 밀리초). 기록을 구분하는 값으로도 쓴다. */
    val createdAtMillis: Long,
    /** 출발지, 경유 순서대로의 탑승 위치, 목적지 */
    val places: List<String>,
    val kmPerLiter: BigDecimal,
    val wonPerLiter: BigDecimal,
    val result: SettlementResult,
) {
    fun hasSameContent(other: SettlementRecord): Boolean =
        places == other.places &&
            kmPerLiter.compareTo(other.kmPerLiter) == 0 &&
            wonPerLiter.compareTo(other.wonPerLiter) == 0 &&
            result == other.result
}

interface SettlementHistoryRepository {
    /** 최근 기록부터 돌려준다. */
    suspend fun records(): List<SettlementRecord>
    suspend fun add(record: SettlementRecord)
    suspend fun delete(createdAtMillis: Long)
}

/**
 * 최근 기록부터 정렬된 [records] 앞에 [record]를 넣고 [MAX_SETTLEMENT_RECORDS]건만 남긴다.
 * 결과 화면을 다시 열어 같은 계산을 반복하면 가장 최근 기록과 같으므로 새로 넣지 않는다.
 */
fun addSettlementRecord(records: List<SettlementRecord>, record: SettlementRecord): List<SettlementRecord> {
    if (records.firstOrNull()?.hasSameContent(record) == true) return records
    return (listOf(record) + records).take(MAX_SETTLEMENT_RECORDS)
}
