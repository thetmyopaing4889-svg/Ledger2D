package com.myanmar.ledger2d.feature.live

import com.myanmar.ledger2d.core.database.LiveDailyResultEntity
import java.time.LocalDate
import java.time.LocalTime

/** No extra Luke request when all currently due daily fields are persisted. */
internal object BackgroundLiveSyncPolicy {
    fun needsFetch(date: LocalDate, now: LocalTime, row: LiveDailyResultEntity?): Boolean {
        if (!isWorkingDay(date) || now.isBefore(MORNING_REFERENCE)) return false
        if (row == null || row.date != date) return true

        fun missingRef(s: String?): Boolean = s == null || !isValidLive2d(s)
        fun missingValue(s: String?): Boolean = s.isNullOrBlank() || s == LIVE_PENDING

        if (missingRef(row.modern930) || missingRef(row.internet930)) return true
        if (!now.isBefore(MORNING_CLOSE)) {
            if (missingRef(row.morning2d) ||
                missingValue(row.morningSet) ||
                missingValue(row.morningValue)
            ) return true
        }
        if (!now.isBefore(AFTERNOON_REFERENCE)) {
            if (missingRef(row.modern200) || missingRef(row.internet200)) return true
        }
        if (!now.isBefore(EVENING_CLOSE)) {
            if (missingRef(row.evening2d) ||
                missingValue(row.eveningSet) ||
                missingValue(row.eveningValue)
            ) return true
        }
        return false
    }
}
