package com.myanmar.ledger2d.feature.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class LiveCollectorTest {
 private fun feed(v:String,t:String,source:String="LUKE")=LiveFeedData(currentYangonDate().toString(),t,v,"1600","20000",
  LiveSessionData("--","--","--",false),LiveSessionData("--","--","--",false),"98","15","40","04",source,
  currentYangonDate().atTime(LocalTime.parse(t)).atZone(ZoneId.of("Asia/Yangon")).toInstant().toEpochMilli())
 private fun obs(f:LiveFeedData)=SourceObservation(f,System.nanoTime()/1_000_000L,System.nanoTime()/1_000_000L,100)
 private fun final(v:String,source:String)=feed(v,"12:01:00",source).copy(
  morning=LiveSessionData(v,"1600","20000",true,if(source=="THAISTOCK2D")"thai-1" else "luke-1","12:01:00"))

 @Test fun `09 30 starts reference fetch`(){assertEquals(LiveWindowAction.REFERENCE_ONLY,liveWindowAction(LocalTime.of(9,30)))}
 @Test fun `11 29 is still waiting`(){assertEquals(LiveWindowAction.NONE,liveWindowAction(LocalTime.of(11,29)))}
 @Test fun `11 30 starts live`(){assertEquals(LiveWindowAction.LIVE_POLLING,liveWindowAction(LocalTime.of(11,30)))}
 @Test fun `11 59 is still live`(){assertEquals(LiveWindowAction.LIVE_POLLING,liveWindowAction(LocalTime.of(11,59)))}
 @Test fun `12 00 is still live`(){assertEquals(LiveWindowAction.LIVE_POLLING,liveWindowAction(LocalTime.of(12,0)))}
 @Test fun `12 01 enters finalizing`(){assertEquals(LiveWindowAction.FINALIZING,liveWindowAction(LocalTime.of(12,1)))}
 @Test fun `14 00 starts reference fetch`(){assertEquals(LiveWindowAction.REFERENCE_ONLY,liveWindowAction(LocalTime.of(14,0)))}
 @Test fun `16 00 starts evening live`(){assertEquals(LiveWindowAction.LIVE_POLLING,liveWindowAction(LocalTime.of(16,0)))}
 @Test fun `16 29 is still evening live`(){assertEquals(LiveWindowAction.LIVE_POLLING,liveWindowAction(LocalTime.of(16,29)))}
 @Test fun `16 30 enters evening finalizing`(){assertEquals(LiveWindowAction.FINALIZING,liveWindowAction(LocalTime.of(16,30)))}


 @Test fun `09 30 reference polls independently from live window`(){
  runTest{
   var n=0
   val scope=CoroutineScope(UnconfinedTestDispatcher(testScheduler))
   val c=LiveCollector(scope,{n++;feed("38","09:30:00")},clock={LocalTime.of(9,30)})
   c.start();advanceTimeBy(100)
   val d=c.state.value as LiveUiState.Data
   assertEquals("98",d.feed?.modern930);assertEquals("15",d.feed?.internet930)
   assertTrue(n>=1);scope.cancel()
  }
 }
 @Test fun `09 30 reference remains eligible during live and after 14 00`(){
  runTest{
   var now=LocalTime.of(11,30);var n=0
   val scope=CoroutineScope(UnconfinedTestDispatcher(testScheduler))
   val c=LiveCollector(scope,{n++;feed("38","09:30:00").copy(modern930="--",internet930="--")},clock={now})
   c.start();advanceTimeBy(61000)
   assertTrue(n>=1)
   now=LocalTime.of(14,0);advanceTimeBy(61000)
   assertTrue(n>=2);scope.cancel()
  }
 }
 @Test fun `14 00 reference starts independently while 09 30 is still missing`(){
  runTest{
   var now=LocalTime.of(14,0);var n=0
   val scope=CoroutineScope(UnconfinedTestDispatcher(testScheduler))
   val c=LiveCollector(scope,{
    n++
    if(n==1) feed("38","14:00:00").copy(modern930="--",internet930="--",modern200="--",internet200="--")
    else feed("38","14:00:00").copy(modern930="--",internet930="--",modern200="98",internet200="15")
   },clock={now})
   c.start();advanceTimeBy(61000)
   val d=c.state.value as LiveUiState.Data
   assertEquals("98",d.feed?.modern200);assertEquals("15",d.feed?.internet200)
   assertTrue(n>=2);scope.cancel()
  }
 }
 @Test fun `09 30 success resets session cards only after both values are valid`(){
  runTest{
   var valid=false
   val scope=CoroutineScope(UnconfinedTestDispatcher(testScheduler))
   val c=LiveCollector(scope,{
    if(valid) feed("38","09:30:00") else feed("38","09:30:00").copy(modern930="--",internet930="--")
   },clock={LocalTime.of(9,30)})
   c.fetchCycle();advanceUntilIdle()
   var d=c.state.value as LiveUiState.Data
   assertEquals("--",d.feed?.modern930);assertEquals("--",d.feed?.morning?.result)
   valid=true
   c.start();advanceTimeBy(100)
   d=c.state.value as LiveUiState.Data
   assertEquals("98",d.feed?.modern930);assertEquals("15",d.feed?.internet930)
   assertEquals("--",d.feed?.morning?.result);assertEquals("--",d.feed?.evening?.result)
  }
 }
 @Test fun `14 00 reference clears its own slot before success`(){
  runTest{
   val scope=CoroutineScope(UnconfinedTestDispatcher(testScheduler))
   val c=LiveCollector(scope,{feed("38","14:00:00").copy(modern200="--",internet200="--")},clock={LocalTime.of(14,0)})
   c.start();advanceTimeBy(100)
   val d=c.state.value as LiveUiState.Data
   assertEquals("--",d.feed?.modern200);assertEquals("--",d.feed?.internet200)
  }
 }

 @Test fun `two matching live sources are confirmed`(){
  val p=obs(feed("38","11:40:00"));val s=obs(feed("38","11:40:00","THAISTOCK2D"))
  val r=resolveLiveState(p,s,Instant.now(),null,null,LocalTime.of(11,40))
  assertEquals(LiveStatus.LIVE_CONFIRMED,r.status);assertTrue(r.heroLive);assertEquals("38",r.hero?.result)
 }
 @Test fun `live mismatch never selects a source`(){
  val last=LiveHeroSnapshot("37","1","2","11:19:00",currentYangonDate().toString())
  val r=resolveLiveState(obs(feed("38","11:40:00")),obs(feed("39","11:40:00","THAISTOCK2D")),Instant.now(),last,null,LocalTime.of(11,40))
  assertEquals(LiveStatus.LIVE_CONFLICT,r.status);assertFalse(r.heroLive);assertEquals("37",r.hero?.result)
 }
 @Test fun `single live source is degraded`(){
  val r=resolveLiveState(obs(feed("38","11:40:00")),null,Instant.now(),null,null,LocalTime.of(11,40))
  assertEquals(LiveStatus.LIVE_DEGRADED,r.status);assertTrue(r.heroLive)
 }
 @Test fun `matching finals become confirmed`(){
  val r=resolveLiveState(obs(final("38","LUKE")),obs(final("38","THAISTOCK2D")),Instant.now(),null,null,LocalTime.of(12,1))
  assertEquals(LiveStatus.FINAL_CONFIRMED,r.status);assertEquals("38",r.hero?.result)
 }
 @Test fun `final mismatch is conflict and disputed result is hidden`(){
  val r=resolveLiveState(obs(final("38","LUKE")),obs(final("39","THAISTOCK2D")),Instant.now(),null,null,LocalTime.of(12,1))
  assertEquals(LiveStatus.FINAL_CONFLICT,r.status);assertEquals("--",r.displayFeed?.morning?.result)
 }
 @Test fun `previous final cache is available for next morning hero`(){
  val old=LiveHeroSnapshot("38","1","2",LIVE_SESSION_EVENING_LABEL,currentYangonDate().minusDays(1).toString())
  val current=feed("--","09:30:00")
  val r=resolveLiveState(obs(current),null,Instant.now(),null,old,LocalTime.of(9,30))
  assertEquals(LiveStatus.WAITING,r.status)
  assertEquals("38",r.hero?.result)
  assertEquals("--",r.displayFeed?.morning?.result)
  assertEquals("--",r.displayFeed?.evening?.result)
 }
 @Test fun `both sources start in one cycle and cycle does not overlap`(){
  runTest{
   var a=0;var b=0;val ga=CompletableDeferred<Unit>();val gb=CompletableDeferred<Unit>()
   val c=LiveCollector(CoroutineScope(UnconfinedTestDispatcher(testScheduler)),{a++;ga.await();feed("38","11:40:00")},{b++;gb.await();feed("38","11:40:00","THAISTOCK2D")})
   c.fetchCycle();c.fetchCycle();advanceTimeBy(100);assertEquals(1,a);assertEquals(1,b)
   ga.complete(Unit);gb.complete(Unit);advanceUntilIdle();assertTrue(c.state.value is LiveUiState.Data)
  }
 }
 @Test fun `live result is not persisted`(){
  runTest{var saved:LiveHeroSnapshot?=null;val c=LiveCollector(CoroutineScope(UnconfinedTestDispatcher(testScheduler)),{feed("38","11:40:00")},{feed("38","11:40:00","THAISTOCK2D")},{LocalTime.of(11,40)},{null},{saved=it})
   c.fetchCycle();advanceUntilIdle();assertEquals(null,saved)
  }
 }
 @Test fun `screen is not required for background polling`(){
  runTest{var n=0;val s=CoroutineScope(UnconfinedTestDispatcher(testScheduler));val c=LiveCollector(s,{n++;feed("38","11:40:00")},clock={LocalTime.of(11,40)});c.start();advanceTimeBy(10000);s.cancel();assertTrue(n>=2)}
 }
}