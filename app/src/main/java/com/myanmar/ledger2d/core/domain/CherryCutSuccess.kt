package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity

/** Distinct prospective metrics. Neither raw nor exclusions enter offered-pair ranks. */
internal object CherryCutSuccess {
    data class Check(val target:DrawIdentity,val rule:CherryCutEvidence,val result:String) {
        val correct:Boolean get()=result !in rule.excluded
    }
    data class Score(val id:String,val name:String,val events:List<Check>) {
        val correct:Int get()=events.count{it.correct}
        val evaluated:Int get()=events.size
    }

    fun checks(draw:CherrySuccessDraw):List<Check> {
        if(draw.snapshot.sourceVersion!=CherrySuccessEngine.CURRENT_RULE_VERSION ||
            !CherrySuccessEngine.beforeCutoff(draw.snapshot.target,draw.snapshot.capturedAtMillis) ||
            !draw.result.matches(Regex("[0-9]{2}")))return emptyList()
        return draw.snapshot.cutRules.map{Check(draw.snapshot.target,it,draw.result)}
    }

    fun scores(draws:List<CherrySuccessDraw>):List<Score> =
        draws.distinctBy{it.snapshot.target}.flatMap(::checks).groupBy{it.rule.id}
            .map{(id,events)->Score(id,events.first().rule.name,events)}
            .sortedBy{it.id}

    /**
     * Raw-only view for lifetime/weekly evaluation. Missing raw evidence is
     * absent, never reconstructed from an old offered snapshot or result.
     */
    fun rawView(draw:CherrySuccessDraw):CherrySuccessDraw? {
        if(draw.snapshot.sourceVersion!=CherrySuccessEngine.CURRENT_RULE_VERSION ||
            !CherrySuccessEngine.beforeCutoff(draw.snapshot.target,draw.snapshot.capturedAtMillis) ||
            !draw.result.matches(Regex("[0-9]{2}")) || draw.snapshot.rawRules.isEmpty())return null
        return draw.copy(checks=draw.snapshot.rawRules.map{rule->
            CherrySuccessCheck(rule,draw.result in rule.predictions,draw.result)
        })
    }
}
