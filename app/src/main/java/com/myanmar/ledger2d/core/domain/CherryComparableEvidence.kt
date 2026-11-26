package com.myanmar.ledger2d.core.domain

/** Compare only frozen evidence captured for the same confirmed draw identities. */
internal object CherryComparableEvidence {
    data class Cohort(
        val raw:List<CherrySuccessDraw>,
        val offered:List<CherrySuccessDraw>,
        val withoutRaw:List<CherrySuccessDraw>,
    )
    fun cohort(draws:List<CherrySuccessDraw>):Cohort {
        val raw=mutableListOf<CherrySuccessDraw>()
        val offered=mutableListOf<CherrySuccessDraw>()
        val withoutRaw=mutableListOf<CherrySuccessDraw>()
        draws.forEach{draw->
            val original=CherryCutSuccess.rawView(draw)
            if(original==null)withoutRaw+=draw
            else {
                raw+=original
                // Only original rule IDs, not Math or an unrelated offered rule.
                val keys=original.checks.map{it.rule.tab to it.rule.id}.toSet()
                offered+=draw.copy(checks=draw.checks.filter{(it.rule.tab to it.rule.id) in keys})
            }
        }
        return Cohort(raw,offered,withoutRaw)
    }

    data class Counts(val rawHits:Int,val rawCount:Int,val offeredHits:Int,val offeredCount:Int,
                      val noOffer:Int)
    fun counts(raw:List<CherrySuccessCheck>,offered:List<CherrySuccessCheck>):Counts {
        val keys=raw.map{it.rule.tab to it.rule.id}.toSet()
        val actual=offered.filter{(it.rule.tab to it.rule.id) in keys}
        val offeredKeys=actual.map{it.rule.tab to it.rule.id}.toSet()
        return Counts(raw.count{it.correct},raw.size,actual.count{it.correct},actual.size,
            keys.count{it !in offeredKeys})
    }
}
