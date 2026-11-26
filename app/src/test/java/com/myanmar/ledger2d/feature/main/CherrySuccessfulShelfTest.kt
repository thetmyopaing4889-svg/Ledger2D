package com.myanmar.ledger2d.feature.main

import com.myanmar.ledger2d.core.domain.CherryTab
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CherrySuccessfulShelfTest {
    private val date=LocalDate.of(2026,11,23)
    private fun rule(id:String):TwoDReaderBookEntry {
        val source=TwoDReaderRule(id,id,1,"",emptyList(),null)
        return TwoDReaderBookEntry(source,id,"",null,emptyList())
    }
    private fun book(id:String,vararg keys:String)=
        TwoDReaderBook(id,id,"#FADDE5",emptyList(),keys.map(::rule))
    private fun component(id:String,metric:CherryLibrarySourceEvidence.Metric,
                          hits:Int=0,met:Boolean=false)=
        CherryLibrarySourceEvidence.Component(id,id,metric,hits,hits,0,emptyList(),met)
    private fun offered(id:String,correct:Boolean):CherryLibrarySuccessScore {
        val event=CherryLibrarySuccessEvent(DrawIdentity(date,DrawSession.MORNING),
            "12",correct,1,setOf(CherryTab.CURRENT))
        return CherryLibrarySuccessScore(id,if(correct)1 else 0,1,listOf(event))
    }

    @Test fun allMetricsSeparateAndSameSourceAppearsOnce() {
        val books=listOf(book("category-a","cherry-001","cherry-033","cherry-073"),
            book("category-b","cherry-001","cherry-019","cherry-020","cherry-018"))
        
        val scores=mapOf("cherry-001" to offered("cherry-001",true))
        val original=mapOf(
            "cherry-001" to listOf(component("s1-day-head-tail",CherryLibrarySourceEvidence.Metric.DRAW,1)),
            "cherry-033" to listOf(component("s6-04",CherryLibrarySourceEvidence.Metric.BIRTH,1)),
            "cherry-073" to listOf(component("cut-dead-triples",CherryLibrarySourceEvidence.Metric.CUT,1)),
            "cherry-019" to listOf(component("s3-six",CherryLibrarySourceEvidence.Metric.WEEK,met=true)),
            "cherry-020" to listOf(component("s3-week",CherryLibrarySourceEvidence.Metric.WEEK,met=true)),
            "unknown" to listOf(component("unknown",CherryLibrarySourceEvidence.Metric.DRAW,1)))
        val result=CherrySuccessfulShelf.entries(books,scores,original)
        assertEquals(listOf("cherry-001","cherry-033","cherry-073","cherry-019"),result.map{it.key})
        
        assertEquals(setOf(CherrySuccessfulShelf.Kind.OFFERED,CherrySuccessfulShelf.Kind.DRAW),result[0].kinds)
        assertEquals(setOf(CherrySuccessfulShelf.Kind.BIRTH),result[1].kinds)
        assertEquals(setOf(CherrySuccessfulShelf.Kind.CUT),result[2].kinds)
        assertEquals(setOf(CherrySuccessfulShelf.Kind.WEEK),result[3].kinds)
    }

    @Test fun crossListed021KeepsTwoDistinctViewsAndNoZeroHitEntries() {
        val books=listOf(book("category-double","cherry-021"),
            book("category-born","cherry-021","cherry-033","cherry-019"))
        
        val original=mapOf(
            "cherry-021#wheel" to listOf(component("s4-wheel-00-0",CherryLibrarySourceEvidence.Metric.BIRTH,1)),
            "cherry-021#born" to listOf(component("s4-born-00",CherryLibrarySourceEvidence.Metric.BIRTH,1)),
            "cherry-033" to listOf(component("s6-04",CherryLibrarySourceEvidence.Metric.BIRTH)),
            "cherry-019" to listOf(component("s3-six",CherryLibrarySourceEvidence.Metric.WEEK)))
        assertEquals(listOf("cherry-021#wheel","cherry-021#born"),
            CherrySuccessfulShelf.entries(books,mapOf("cherry-033" to offered("cherry-033",false)),original).map{it.key})
    }
}
