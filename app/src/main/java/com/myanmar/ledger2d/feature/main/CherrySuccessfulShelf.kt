package com.myanmar.ledger2d.feature.main

/** A derived evidence index, not a ranking or a new persistent score. */
internal object CherrySuccessfulShelf {
    enum class Kind { OFFERED, DRAW, BIRTH, WEEK, CUT }
    data class Entry(val key:String,val kinds:Set<Kind>)

    fun entries(
        books:List<TwoDReaderBook>,
        offered:Map<String,CherryLibrarySuccessScore>,
        original:Map<String,List<CherryLibrarySourceEvidence.Component>>,
    ):List<Entry> {
        // Keep existing source book order. Cross-listed 021 projections retain
        // independent wheel and born keys; every other canonical ID appears once.
        val keys=books.flatMap{book->book.rules.map{rule->
            CherryLibrarySuccessBridge.entryScoreKey(book.id,rule.id)
        }}.distinct()
        return keys.mapNotNull{key->
            val kinds=linkedSetOf<Kind>()
            if((offered[key]?.correct?:0)>0)kinds+=Kind.OFFERED
            original[key].orEmpty().forEach{component->
                when(component.metric) {
                    CherryLibrarySourceEvidence.Metric.DRAW ->
                        if(component.correct>0)kinds+=Kind.DRAW
                    CherryLibrarySourceEvidence.Metric.BIRTH ->
                        if(component.correct>0)kinds+=Kind.BIRTH
                    CherryLibrarySourceEvidence.Metric.WEEK ->
                        if(component.weeklyTargetMet && component.id!="s3-week")kinds+=Kind.WEEK
                    CherryLibrarySourceEvidence.Metric.CUT ->
                        if(component.correct>0)kinds+=Kind.CUT
                }
            }
            if(kinds.isEmpty())null else Entry(key,kinds)
        }
    }
}
