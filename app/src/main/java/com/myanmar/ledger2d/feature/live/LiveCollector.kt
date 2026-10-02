package com.myanmar.ledger2d.feature.live

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.*

private val YANGON = ZoneId.of("Asia/Yangon")
internal const val NORMAL_POLL_INTERVAL_MS = 3_000L
internal const val CLOSING_POLL_INTERVAL_MS = 1_500L
internal const val CYCLE_DEADLINE_MS = 3_500L
internal const val FINAL_GRACE_MS = 30_000L
internal const val SOURCE_FRESHNESS_MS = 5_000L
internal const val SOURCE_TIME_SKEW_MS = 2_000L
internal const val LIVE_REFERENCE_FETCH_INTERVAL_MS = 60_000L

private val MORNING_LIVE = LocalTime.of(11,30)
private val MORNING_CLOSE = LocalTime.of(12,1)
private val MORNING_FINAL = LocalTime.of(12,1)
private val EVENING_LIVE = LocalTime.of(16,0)
private val EVENING_CLOSE = LocalTime.of(16,30)
private val EVENING_FINAL = LocalTime.of(16,30)

internal const val LIVE_PENDING = "--"
internal const val LIVE_SESSION_MORNING_LABEL = "12:01 PM"
internal const val LIVE_SESSION_EVENING_LABEL = "4:30 PM"

data class LiveHeroSnapshot(
    val result: String,
    val set: String,
    val value: String,
    val sessionLabel: String,
    val date: String,
)

internal enum class LiveWindowAction { NONE, REFERENCE_ONLY, LIVE_POLLING, FINALIZING }
enum class LiveStatus { WAITING,LIVE_CONFIRMED,LIVE_DEGRADED,WAITING_FOR_ALIGNMENT,LIVE_CONFLICT,STALE,FINALIZING,WAITING_FOR_PRIMARY,DRAW_FREEZE,RESULT_AVAILABLE,FINAL_CONFIRMED,DEGRADED_FINAL,FINAL_CONFLICT,STALE_PRIMARY }

internal data class SourceObservation(val feed: LiveFeedData,val fetchedAtElapsedMs:Long,val requestStartedElapsedMs:Long,val roundTripMs:Long)
internal data class LiveResolution(val displayFeed:LiveFeedData?,val hero:LiveHeroSnapshot?,val heroLive:Boolean,val status:LiveStatus,val message:String,val sourceCount:Int,val staleAgeMs:Long)

internal fun liveWindowAction(t:LocalTime):LiveWindowAction=when{
 t.isBefore(LocalTime.of(9,30))->LiveWindowAction.NONE
 t.isBefore(LocalTime.of(9,31))->LiveWindowAction.REFERENCE_ONLY
 t.isBefore(MORNING_LIVE)->LiveWindowAction.NONE
 t.isBefore(MORNING_CLOSE)->LiveWindowAction.LIVE_POLLING
t<=MORNING_FINAL->LiveWindowAction.FINALIZING
 t.isBefore(LocalTime.of(14,0))->LiveWindowAction.NONE
 t.isBefore(LocalTime.of(14,1))->LiveWindowAction.REFERENCE_ONLY
 t.isBefore(EVENING_LIVE)->LiveWindowAction.NONE
 t.isBefore(EVENING_CLOSE)->LiveWindowAction.LIVE_POLLING
 t<=EVENING_FINAL->LiveWindowAction.FINALIZING
 else->LiveWindowAction.NONE
}
internal fun currentYangonDate():LocalDate=LocalDate.now(YANGON)
internal fun canonicalDate(raw:String):LocalDate?=runCatching{LocalDate.parse(raw.trim())}.getOrElse{runCatching{LocalDate.parse(raw.trim(),java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}.getOrNull()}
internal fun parseDecisionInstant(f:LiveFeedData):Instant?{
 f.serverTimeEpochMs?.let{return Instant.ofEpochMilli(it)}
 val raw=f.currentTime.trim()
 if(raw.isBlank()||raw==LIVE_PENDING)return null
 return try{
  when{
   raw.contains("T")->Instant.parse(raw)
   raw.contains(" ")->LocalDateTime.parse(raw.replace(' ','T')).atZone(YANGON).toInstant()
   else->{
    val d=canonicalDate(f.date)?:return null
    LocalDateTime.of(d,LocalTime.parse(raw.take(8))).atZone(YANGON).toInstant()
   }
  }
 }catch(_:Exception){null}
}
internal fun isValidLive2d(v:String)=v.matches(Regex("^[0-9]{2}$"))
private fun validMoney(v:String)=v.isNotBlank()&&v!=LIVE_PENDING
private fun currentDay(f:LiveFeedData)=canonicalDate(f.date)==currentYangonDate()
private fun age(o:SourceObservation?)=if(o==null)Long.MAX_VALUE else maxOf(0,System.nanoTime()/1_000_000L-o.fetchedAtElapsedMs)

private fun fresh(o:SourceObservation?):Boolean{
 if(o==null||!currentDay(o.feed)||!isValidLive2d(o.feed.live)||!validMoney(o.feed.liveSet)||!validMoney(o.feed.liveVal))return false
 val t=parseDecisionInstant(o.feed)?:return false
 return age(o)<=SOURCE_FRESHNESS_MS&&t.isAfter(Instant.EPOCH)
}
private fun finalValid(o:SourceObservation?,s:LiveSessionData?,thai:Boolean):Boolean{
 if(o==null||s==null||!s.finalized||!currentDay(o.feed)||!isValidLive2d(s.result)||!validMoney(s.set)||!validMoney(s.value))return false
 if(thai&&s.historyId.isNullOrBlank())return false
 return parseDecisionInstant(o.feed)!=null
}
private fun aligned(a:SourceObservation,b:SourceObservation):Boolean{
 val x=parseDecisionInstant(a.feed)?:return false; val y=parseDecisionInstant(b.feed)?:return false
 return kotlin.math.abs(x.toEpochMilli()-y.toEpochMilli())<=SOURCE_TIME_SKEW_MS
}
private fun label(t:LocalTime)=if(!t.isBefore(EVENING_LIVE))LIVE_SESSION_EVENING_LABEL else LIVE_SESSION_MORNING_LABEL
private fun resetMorningAndEveningForNewDay(f:LiveFeedData,t:LocalTime):LiveFeedData = if (!t.isBefore(LocalTime.of(9,30)) && t.isBefore(MORNING_LIVE) && currentDay(f)) f.copy(
 morning=LiveSessionData(LIVE_PENDING,LIVE_PENDING,LIVE_PENDING,false),
 evening=LiveSessionData(LIVE_PENDING,LIVE_PENDING,LIVE_PENDING,false),
) else f

private fun finalOf(f:LiveFeedData)=when{
 f.evening.finalized->LiveHeroSnapshot(f.evening.result,f.evening.set,f.evening.value,LIVE_SESSION_EVENING_LABEL,f.date)
 f.morning.finalized->LiveHeroSnapshot(f.morning.result,f.morning.set,f.morning.value,LIVE_SESSION_MORNING_LABEL,f.date)
 else->null
}

private fun clockTimeFromObservations(p:SourceObservation?,s:SourceObservation?,now:Instant):LocalTime? = (p?.feed?.let(::parseDecisionInstant) ?: s?.feed?.let(::parseDecisionInstant))?.atZone(YANGON)?.toLocalTime()

internal fun resolveLiveState(p:SourceObservation?,s:SourceObservation?,now:Instant,lastLive:LiveHeroSnapshot?,cachedFinal:LiveHeroSnapshot?,scheduleTime:LocalTime?=null):LiveResolution{
 val pv=fresh(p); val sv=fresh(s); val count=listOf(pv,sv).count{it}; val pt=p?.feed?.let(::parseDecisionInstant)
 val t=scheduleTime?:clockTimeFromObservations(p,s,now)?:return LiveResolution(p?.feed?:s?.feed,lastLive?:cachedFinal,false,LiveStatus.WAITING_FOR_ALIGNMENT,"WAITING_FOR_PRIMARY • CLOCK",count,maxOf(age(p),age(s)))
 when(liveWindowAction(t)){
  LiveWindowAction.LIVE_POLLING->{
   if(!pv&&!sv)return LiveResolution(p?.feed?:s?.feed,lastLive,false,LiveStatus.STALE,"STALE • NO FRESH SOURCE",0,maxOf(age(p),age(s)))
   if(pv&&sv){
    if(!aligned(p!!,s!!))return LiveResolution(p.feed,lastLive,false,LiveStatus.WAITING_FOR_ALIGNMENT,"SYNCING • TIME ALIGNMENT",2,maxOf(age(p),age(s)))
    if(p.feed.live==s.feed.live){
     val h=LiveHeroSnapshot(p.feed.live,p.feed.liveSet,p.feed.liveVal,p.feed.currentTime,p.feed.date)
     return LiveResolution(p.feed,h,true,LiveStatus.LIVE_CONFIRMED,"LIVE_CONFIRMED • CROSS-SOURCE MATCH",2,0)
    }
    return LiveResolution(p.feed,lastLive,false,LiveStatus.LIVE_CONFLICT,"LIVE_CONFLICT • DATA MISMATCH",2,maxOf(age(p),age(s)))
   }
   val o=if(pv)p!! else s!!; val h=LiveHeroSnapshot(o.feed.live,o.feed.liveSet,o.feed.liveVal,o.feed.currentTime,o.feed.date)
   return LiveResolution(o.feed,h,true,LiveStatus.LIVE_DEGRADED,if(pv)"LIVE_DEGRADED • LUKE" else "LIVE_DEGRADED • THAISTOCK2D",1,age(o))
  }
  LiveWindowAction.FINALIZING->{
   if(!pv)return LiveResolution(p?.feed,lastLive,false,LiveStatus.WAITING_FOR_PRIMARY,"FINALIZING • WAITING_FOR_PRIMARY",count,maxOf(age(p),age(s)))
   val evening=!t.isBefore(EVENING_LIVE); val ps=if(evening)p!!.feed.evening else p!!.feed.morning; val ss=s?.let{if(evening)it.feed.evening else it.feed.morning}
   val pf=finalValid(p,ps,false); val sf=finalValid(s,ss,true)
   if(pf&&sf){
    if(ps.result==ss!!.result){
     val h=LiveHeroSnapshot(ps.result,ps.set,ps.value,label(t),p.feed.date)
     return LiveResolution(p.feed,h,false,LiveStatus.FINAL_CONFIRMED,"FINAL_CONFIRMED • CROSS-SOURCE MATCH",2,0)
    }
    val safe=if(evening)p.feed.copy(evening=LiveSessionData(LIVE_PENDING,LIVE_PENDING,LIVE_PENDING,false)) else p.feed.copy(morning=LiveSessionData(LIVE_PENDING,LIVE_PENDING,LIVE_PENDING,false))
    return LiveResolution(safe,lastLive,false,LiveStatus.FINAL_CONFLICT,"FINAL_CONFLICT • DATA MISMATCH",2,maxOf(age(p),age(s)))
   }
   if(pf){
    val h=LiveHeroSnapshot(ps.result,ps.set,ps.value,label(t),p.feed.date); val elapsed=now.toEpochMilli()-pt!!.toEpochMilli()
    return LiveResolution(p.feed,h,false,if(elapsed>=FINAL_GRACE_MS)LiveStatus.DEGRADED_FINAL else LiveStatus.RESULT_AVAILABLE,if(elapsed>=FINAL_GRACE_MS)"DEGRADED_FINAL • UNVERIFIED LUKE" else "RESULT_AVAILABLE • LUKE UNVERIFIED",1,age(p))
   }
   return LiveResolution(p.feed,lastLive,false,LiveStatus.DRAW_FREEZE,"DRAW_FREEZE • FINALIZING",count,age(p))
  }
  LiveWindowAction.NONE,LiveWindowAction.REFERENCE_ONLY->{
   val raw=p?.feed?:s?.feed
   val f=raw?.let{resetMorningAndEveningForNewDay(it,t)} ?: raw
   val fin=f?.let(::finalOf)
   if(fin!=null&&currentDay(f))return LiveResolution(f,fin,false,LiveStatus.FINAL_CONFIRMED,"FINAL_CONFIRMED",count,0)
   val previousHero=if(t.isBefore(MORNING_LIVE)) cachedFinal else null
   return LiveResolution(f,previousHero,false,LiveStatus.WAITING,"WAITING",count,maxOf(age(p),age(s)))
  }
 }
}

internal class LiveCollector(
 private val scope:CoroutineScope,
 private val fetcher:suspend()->LiveFeedData?,
 private val secondaryFetcher:(suspend()->LiveFeedData?)?=null,
 private val clock:()->LocalTime={LocalTime.now(YANGON)},
 cacheLoader:()->LiveHeroSnapshot?={null},
 private val cacheSaver:(LiveHeroSnapshot)->Unit={}
){
 private val _state=MutableStateFlow<LiveUiState>(LiveUiState.Loading); val state:StateFlow<LiveUiState> = _state.asStateFlow()
 private var cycle:Job?=null
 private var reference930Cycle:Job?=null
 private var reference200Cycle:Job?=null
 private var reference930CompleteDate:LocalDate?=null
 private var reference200CompleteDate:LocalDate?=null
 private var referenceResetDate:LocalDate?=null
 private var reference930:Pair<String,String>?=null
 private var reference200:Pair<String,String>?=null
 private var p:SourceObservation?=null; private var s:SourceObservation?=null
 private var lastLive:LiveHeroSnapshot?=null; private var lastFinal:LiveHeroSnapshot?=null
 init{val c=cacheLoader();if(c!=null){lastFinal=c;_state.value=LiveUiState.Data(null,c,false,false,status=LiveStatus.FINAL_CONFIRMED,sourceMessage="FINAL_CONFIRMED • CACHED")}}
 fun fetchCycle(){
  if(cycle?.isActive==true)return
  cycle=scope.launch{
   val pj=launchSource(fetcher,true);val sj=secondaryFetcher?.let{launchSource(it,false)}
   withTimeoutOrNull(CYCLE_DEADLINE_MS){while(pj.isActive||sj?.isActive==true)delay(10)}
   if(pj.isActive)pj.cancel();sj?.cancel();publish()
  }
 }
 private fun launchSource(src:suspend()->LiveFeedData?,primary:Boolean)=scope.launch{
  val started=System.nanoTime()/1_000_000L;val f=try{src()}catch(_:Exception){null}
  if(f!=null){
   val end=System.nanoTime()/1_000_000L
   val normalized=f
   val o=SourceObservation(normalized,end,started,end-started)
   if(primary)p=o else s=o
  }
 }
 private fun validReferencePair(f:LiveFeedData,modern:String,internet:String):Boolean=
  currentDay(f)&&isValidLive2d(modern)&&isValidLive2d(internet)

 private fun mergeReferenceIntoFeed(base:LiveFeedData?):LiveFeedData?{
  if(base==null)return null
  var out=base
  reference930?.let{out=out.copy(modern930=it.first,internet930=it.second)}
  reference200?.let{out=out.copy(modern200=it.first,internet200=it.second)}
  if(referenceResetDate==currentYangonDate()){
   out=out.copy(
    morning=LiveSessionData(LIVE_PENDING,LIVE_PENDING,LIVE_PENDING,false),
    evening=LiveSessionData(LIVE_PENDING,LIVE_PENDING,LIVE_PENDING,false),
   )
  }
  return out
 }

 private fun fetchReference930Cycle(){
  if(reference930Cycle?.isActive==true||reference930CompleteDate==currentYangonDate())return
  reference930Cycle=scope.launch{
   reference930=LIVE_PENDING to LIVE_PENDING
   publish()
   while(isActive){
    if(clock().isBefore(LocalTime.of(9,30)))return@launch
    val f=withTimeoutOrNull(CYCLE_DEADLINE_MS){try{fetcher()}catch(_:Exception){null}}
    if(f==null){
     publish()
     delay(LIVE_REFERENCE_FETCH_INTERVAL_MS)
     continue
    }
    if(validReferencePair(f,f.modern930,f.internet930)){
     reference930=f.modern930 to f.internet930
     reference930CompleteDate=currentYangonDate()
     referenceResetDate=currentYangonDate()
     publish()
     return@launch
    }
    publish()
    delay(LIVE_REFERENCE_FETCH_INTERVAL_MS)
   }
  }
 }

 private fun fetchReference200Cycle(){
  if(reference200Cycle?.isActive==true||reference200CompleteDate==currentYangonDate())return
  reference200Cycle=scope.launch{
   reference200=LIVE_PENDING to LIVE_PENDING
   publish()
   while(isActive){
    if(clock().isBefore(LocalTime.of(14,0)))return@launch
    val f=withTimeoutOrNull(CYCLE_DEADLINE_MS){try{fetcher()}catch(_:Exception){null}}
    if(f==null){
     publish()
     delay(LIVE_REFERENCE_FETCH_INTERVAL_MS)
     continue
    }
    if(validReferencePair(f,f.modern200,f.internet200)){
     reference200=f.modern200 to f.internet200
     reference200CompleteDate=currentYangonDate()
     publish()
     return@launch
    }
    publish()
    delay(LIVE_REFERENCE_FETCH_INTERVAL_MS)
   }
  }
 }

 private fun ensureReferenceJobs(t:LocalTime){
  val today=currentYangonDate()
  if(t.isBefore(LocalTime.of(9,30)))return
  if(reference930CompleteDate!=today)fetchReference930Cycle()
  if(!t.isBefore(LocalTime.of(14,0))&&reference200CompleteDate!=today)fetchReference200Cycle()
 }

 private fun publish(){
  val displayPrimary=p?.let{it.copy(feed=mergeReferenceIntoFeed(it.feed) ?: it.feed)}
  val r=resolveLiveState(displayPrimary,s,Instant.now(),lastLive,lastFinal,clock())
  if(r.status==LiveStatus.LIVE_CONFIRMED)lastLive=r.hero
  if(r.status==LiveStatus.FINAL_CONFIRMED&&r.hero!=null&&r.hero!=lastFinal){lastFinal=r.hero;cacheSaver(r.hero)}
  _state.value=LiveUiState.Data(r.displayFeed,r.hero,r.heroLive,p==null&&s==null,r.secondaryFeed(),r.message,r.status,r.staleAgeMs)
 }
 private fun LiveResolution.secondaryFeed():LiveFeedData?=s?.feed
 fun start(){
  scope.launch{while(isActive){
   val t=clock()
   val action=liveWindowAction(t)
   when(action){
    LiveWindowAction.LIVE_POLLING,LiveWindowAction.FINALIZING->fetchCycle()
    LiveWindowAction.NONE,LiveWindowAction.REFERENCE_ONLY->Unit
   }
   delay(when{
    action==LiveWindowAction.FINALIZING->CLOSING_POLL_INTERVAL_MS
    action==LiveWindowAction.LIVE_POLLING->NORMAL_POLL_INTERVAL_MS
    else->NORMAL_POLL_INTERVAL_MS
   })
  }}
  scope.launch{while(isActive){
   ensureReferenceJobs(clock())
   delay(LIVE_REFERENCE_FETCH_INTERVAL_MS)
  }}
 }
 companion object{
  @Volatile private var shared:LiveCollector?=null
  val instance:LiveCollector get()=shared?:error("LiveCollector not started")
  fun startOnce(context:Context){if(shared!=null)return;synchronized(this){if(shared!=null)return;val c=LiveCollector(CoroutineScope(SupervisorJob()+Dispatchers.Default),{withContext(Dispatchers.IO){LiveApi.fetch()}},{withContext(Dispatchers.IO){LiveApi.fetchThaiStock()}},{LocalTime.now(YANGON)},{LiveCacheStore.load(context)},{LiveCacheStore.save(context,it)});shared=c;c.start();c.fetchCycle()}}
 }
}

internal object LiveCacheStore{
 private const val PREFS_NAME="live_display_cache";private const val KEY="latest_final"
 fun load(c:Context):LiveHeroSnapshot?=try{val o=JSONObject(c.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE).getString(KEY,null)?:return null);if(o.optString("state")!="FINAL_CONFIRMED")null else LiveHeroSnapshot(o.getString("result"),o.getString("set"),o.getString("value"),o.getString("sessionLabel"),o.getString("date"))}catch(_:Exception){null}
 fun save(c:Context,s:LiveHeroSnapshot){runCatching{val o=JSONObject().put("result",s.result).put("set",s.set).put("value",s.value).put("sessionLabel",s.sessionLabel).put("date",s.date).put("state","FINAL_CONFIRMED");c.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE).edit().putString(KEY,o.toString()).apply()}}
}
