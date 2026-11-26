package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import java.time.LocalDate

/** Better Data only; preserve the shared historical/Research merge. */
internal object CherryVerifiedHistory {
    private fun priority(source:HistoricSource):Int=when(source) {
        HistoricSource.WINNER->3
        HistoricSource.DAILY->2
        HistoricSource.HISTORY->1
    }

    fun normalize(rows:List<HistoricDraw>,closed:Set<LocalDate> = emptySet()):List<HistoricDraw> =
        rows.asSequence().filter{validHistoric2D(it.digit)&&DrawSchedule.isWorkingDate(it.date,closed)}
            .groupBy{DrawIdentity(it.date,it.session)}
            .values.mapNotNull{group->
                val best=group.maxOf{priority(it.source)}
                val highest=group.filter{priority(it.source)==best}
                highest.firstOrNull()?.takeIf{highest.all{row->row.digit==it.digit}}
            }.sortedWith(compareBy<HistoricDraw>{it.date}.thenBy{it.session.ordinal})

    fun results(rows:List<HistoricDraw>,closed:Set<LocalDate> = emptySet()):Map<DrawIdentity,String> =
        normalize(rows,closed).associate{DrawIdentity(it.date,it.session) to it.digit}
}
