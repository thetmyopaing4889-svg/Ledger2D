package com.myanmar.ledger2d.feature.main

/** Source references are original legacy excerpts or labeled summaries of the user-supplied images. */
internal data class CherryOriginalReference(
    val section: Int,
    val label: String,
    val quote: String,
)

internal object CherryRuleSources {
    private val digits = listOf("၀","၁","၂","၃","၄","၅","၆","၇","၈","၉")
    private val heading = Regex("""^အပိုင်း \([၁၂၃၄၅၆၇၈၉]\)""")

    /** Concise editorial summaries of user-supplied images, not verbatim quotations. */
    private fun addedSource(id: String): CherryOriginalReference? {
        val pageAndQuote = when (id) {
            "new-mob-double" -> 1 to
                "လူမိုက်ကြီးအပူး 17, 20, 80, 56, 51 ထဲမှ တစ်ကွက်ထွက်ပြီး အပူးလိုက် ဆတိုးထွက်တတ်သည်။"
            "new-mob-wheel" -> 1 to
                "အပေါက်လူမိုက်ဂဏန်းထဲမှ ၁ ကွက်ထွက်လျှင် 1267 အခွေလိုက် ထွက်တတ်သည်။"
            "new-bk-six" -> 2 to
                "6 ဘရိတ်ထွက်တိုင်း 1649 (1, 6, 4, 9) အခွေ လိုက်ထွက်တတ်သည်။"
            "new-born-50" -> 3 to
                "50 ထွက်ပြီးတိုင်း 3 ထိပ်/8 ထိပ် (37, 82) လာတတ်သည်။"
            "new-born-75" -> 1 to
                "မနက်ပိုင်း 75 ပြန်ထွက်လျှင် နောက် ၂ ရက်အတွင်း 45789 အခွေ လာတတ်သည်။"
            "new-bk-19r" -> 3 to
                "19R ထွက်တိုင်း ၅ ရက်အတွင်း 1 ဘရိတ် (01, 29, 38, 47, 56)R လိုက်တတ်သည်။"
            "new-special-khant" -> 3 to
                "2D Ko Khant — 98, 11, 91 ကျပြီးတိုင်း 0 ထိပ် ဆက်စပ်မူ။"
            "s6-12-wheel" -> 3 to
                "12R ထွက်ပြီးတိုင်း 48R မွေးထိုး (4789 အပြီးခွေ)။"
            else -> null
        } ?: return null
        return CherryOriginalReference(
            pageAndQuote.first,
            "ဓာတ်ပုံအကျဉ်းချုပ် • စာမျက်နှာ ${pageAndQuote.first}",
            pageAndQuote.second,
        )
    }

    // Exact reader text at the approved library baseline. These are reader
    // excerpts, not newly transcribed photo originals; retain excluded branches
    // in the reference while the calculator uses only authorized branches.
    private fun clarifiedReaderSource(id:String):CherryOriginalReference? = when(id) {
        "s1-day-head-tail" -> CherryOriginalReference(
            1, "မူစာအုပ် • ထိပ်တူ / ပိတ်တူမူ",
            "မနက်ပိုင်းနှင့် ညနေပိုင်းတွင် ထိပ်စီးချင်းတူ သို့မဟုတ် နောက်ပိတ်ချင်းတူ ထွက်ခဲ့ပါက တူသည့်ဂဏန်း၏ ရှေ့တစ်လုံး (၁ လျှော့ဂဏန်း) လာတတ်သည်။ အကယ်၍ မလာခဲ့ပါက 0 ဆင်းတတ်ပြီး၊ 0 မဆင်းပါက အပူး ဆင်းတတ်သည်။")
        "s1-day-break" -> CherryOriginalReference(
            1, "မူစာအုပ် • ဘရိတ်တူမူ",
            "မနက်ပိုင်းနှင့် ညနေပိုင်း ဘရိတ်တူခဲ့ပါက 0 လာတတ်သည်။ 0 မလာခဲ့ပါက အပူး ဆင်းတတ်ပြီး၊ အပူးမဆင်းပါက ပြန်ကျဂဏန်း လာတတ်သည်။")
        "s1-day-diagonal" -> CherryOriginalReference(
            1, "မူစာအုပ် • ဘေးတစ်စောင်း ဘရိတ်တူမူ",
            "ဘေးတစ်စောင်း ဘရိတ်တူ ထွက်ခဲ့ပါက နက္ခတ် နှင့် ပြန်ကျဂဏန်း ကို ပြန်လည်ကစားရမည်။")
        "s6-57" -> CherryOriginalReference(
            6, "မူစာအုပ် • (57) R ထွက်လျှင်",
            "(57) R ထွက်လျှင်: 357 အထိ ပြန်ထိုးပါ။")
        "s8-zero-follow" -> CherryOriginalReference(
            8, "မူစာအုပ် • 0 ထွက်လျှင် ပတ်သီး/ပိတ်မူ",
            "0 ထွက်လျှင် ➔ 7 သာပတ်ပါ။")
        "s8-evening-four" -> CherryOriginalReference(
            8, "မူစာအုပ် • ညနေပိုင်း 4 ထိပ်မူ",
            "ညနေပိုင်းတွင် 4 ထိပ် လာတိုင်း ပါဝါ သို့မဟုတ် နက္ခတ် လိုက်သည်။")
        "s9-wailin" -> CherryOriginalReference(
            9, "မူစာအုပ် • WaiLin 2D နေ့စဉ် တစ်လုံးပိုင်မူ",
            "မနေ့က ထွက်သွားသော မနက်ဂဏန်းနှင့် ညနေဂဏန်းကို ချရေးပြီး (မနက်ထိပ်စီး + ညနေထိပ်စီး + ညနေနောက်ပိတ်) သုံးလုံးပေါင်းလဒ်သည် ယနေ့အတွက် တစ်လုံးပိုင် ဖြစ်သည်။")
        "s6-06" -> CherryOriginalReference(
            6, "မူစာအုပ် • (06) R ထွက်လျှင်",
            "(06) R ထွက်လျှင်: (၄) ပွဲ ဆတိုးဖြင့် ညီအစ်ကို အပိုင်မွေးရမည်။")
        "s5-mon-ones" -> CherryOriginalReference(
            5, "မူစာအုပ် • တနင်္လာနေ့ 1 နှစ်လုံး/သုံးလုံး မူ",
            "တနင်္လာနေ့ နေကုန်ထွက်ဂဏန်းတွင် 1 နှစ်လုံး (သို့မဟုတ်) 1 သုံးလုံး ပါနေခဲ့လျှင် တစ်ပတ်အတွင်း (9) ဘရိတ် အပိုင်လိုက်ကစားပါ (09 - 18 - 27 - 36 - 45 R / အထွက်အများဆုံးဂဏန်းမှာ 09 - 27 R ဖြစ်သည်)။")
        "s5-mon-nine" -> CherryOriginalReference(
            5, "မူစာအုပ် • တနင်္လာနေ့တွင် (9) လာပါက",
            "အပူး မွေးရမည်။")
        "s5-mon-three" -> CherryOriginalReference(
            5, "မူစာအုပ် • တနင်္လာနေ့တွင် (3) လာပါက",
            "အပြန်ဂဏန်း (ပြန်ကျ) မွေးရမည်။")
        "s5-mon-five-break", "s5-mon-five-digit" -> CherryOriginalReference(
            5, "မူစာအုပ် • တနင်္လာနေ့တွင် (5) လာပါက",
            "ဘူ (၀ ဘရိတ်) / တစ်ဆယ်ပေါင်း မွေးရမည်ဖြစ်ပြီး၊ ကျန် (၄) ရက်အတွင်း (5) တစ်လုံး ရာနှုန်းပြည့် ပြန်ဆင်းပေးသည်။")
        "s8-mon-seven" -> CherryOriginalReference(
            8, "မူစာအုပ် • တနင်္လာမနက် 7 ထိပ်မူ",
            "တနင်္လာနေ့မနက်တွင် 7 ထိပ် ကျလာတိုင်း 3 ဇွတ်လိုက်ပါ။ ထို့ပြင် ကြာသပတေးနေ့တွင် 5 တစ်လုံး နှင့် သောကြာနေ့တွင် 4 ကျိန်းသေ ဆင်းသည်။")
        "s8-thufri-power" -> CherryOriginalReference(
            8, "မူစာအုပ် • ကြာသပတေး/သောကြာ ထိပ်ပေါင်း ပါဝါတွဲမူ",
            "ကြာသပတေးမနက် ထိပ်စီးနှင့် သောကြာမနက် ထိပ်စီးနှစ်လုံးသည် ပါဝါအတွဲ (05, 16, 27, 38, 49) ဖြစ်ခဲ့လျှင် နောက်တစ်ပတ်အတွင်း (26, 29, 28, 48, 46, 49) R ကို ဆတိုးမွေးထိုးပါ။")
        "new-bk-evening7" -> CherryOriginalReference(
            10, "မူစာအုပ် • ညနေ 7 ပိတ်မူ",
            "ညနေ 7 ပိတ်ထွက်ပြီး နောက်ရက်များတွင် 5/0 ပတ်သီး သို့မဟုတ် 3 ဘရိတ် လိုက်တတ်သည်ဟု ဖော်ပြထားသည်။")
        "new-day-mon-b4", "new-day-mon-t6", "new-day-mon-79", "new-day-mon-89" -> CherryOriginalReference(
            10, "မူစာအုပ် • တနင်္လာနေ့ဆက်စပ်မူ",
            "4 ဘရိတ်ကျလျှင် 27, 45။ 6 ပိတ်ကျလျှင် (09, 27, 35, 49, 56, 89)R။ 79R ကျလျှင် (12, 15, 17, 29, 36, 69)R။ 89R ကျလျှင် 2/7/4/9 ဘရိတ်။ နောက်ဆုံးပိတ်တူနေပါက (94, 95, 96, 97, 98)R ကိုဖော်ပြထားသည်။")
        "new-day-tue-b3", "new-day-tue-56", "new-day-tue-h5", "new-day-tue-b0", "new-day-tue-b4" -> CherryOriginalReference(
            10, "မူစာအုပ် • အင်္ဂါနေ့ဆက်စပ်မူ",
            "3 ဘရိတ်ကျလျှင် 5/9 ဘရိတ်။ 56R ထွက်လျှင် 13R၊ 5 ထိပ်ကျလျှင် 63R၊ 0 ဘရိတ်ကျလျှင် 0 ဘရိတ်၊ 4 ဘရိတ်ကျလျှင် 8 ဘရိတ်ကို ဖော်ပြထားသည်။")
        "new-day-wed-b9", "new-day-wed-b5", "new-day-wed-t6" -> CherryOriginalReference(
            10, "မူစာအုပ် • ဗုဒ္ဓဟူးနေ့ဆက်စပ်မူ",
            "9 ဘရိတ်ကျလျှင် 1456 အခွေ၊ 5 ဘရိတ်ကျလျှင် 678 ဘရိတ်။ 6 ပိတ်ကျလျှင် 8 ထိပ်/8 ဘရိတ်။ 5 သုံးလုံးတွင် 0 ဘရိတ်ကျလျှင် 2479 ဘရိတ်ကို ဖော်ပြထားသည်။")
        "new-groups-weekly-nine", "new-groups-weekly-break", "new-groups-weekly-wheel" -> CherryOriginalReference(
            10, "မူစာအုပ် • အပတ်စဉ်မပျောက်တတ်သည့် မူများ",
            "9 လုံး၊ 1 ဘရိတ်နှင့် 4679 အခွေကို တစ်ပတ်အတွင်း စောင့်ကြည့်ရန် ဖော်ပြထားသည့် ရင်းမြစ်အဆိုသုံးချက်။")
        else -> null
    }

    private fun section(source:String,number:Int):List<String> {
        if(number !in 1..9)return emptyList()
        val lines=source.lineSequence().map{it.trim()}.toList()
        val prefix="အပိုင်း (${digits[number]})"
        val start=lines.indexOfFirst{it.startsWith(prefix)}
        if(start<0)return emptyList()
        val offset=lines.drop(start+1).indexOfFirst{heading.containsMatchIn(it)}
        val end=if(offset<0)lines.size else start+offset+1
        return lines.subList(start+1,end).filter{
            it.isNotBlank()&&!it.matches(Regex("_+"))
        }
    }

    fun lookup(source:String?,ruleId:String,detail:String=""):CherryOriginalReference? {
        if(source.isNullOrBlank())return null
        val id=ruleId.substringBefore('@')
        clarifiedReaderSource(id)?.let { return it }
        addedSource(id)?.let { return it }
        val part:Int
        val label:String
        val raw:List<String>
        fun bullet(num:Int,term:String)=section(source,num)
            .firstOrNull{it.startsWith("*")&&term in it}

        when {
            id.startsWith("s2-p-")||id.startsWith("s2-h-")||
                id.startsWith("s2-f-")||id.startsWith("s2-return-")||id=="s2-tail6-49"||id=="s2-8head"->{
                part=2
                val digit=when{
                    id=="s2-8head"->8
                    id=="s2-tail6-49"->6
                    id.startsWith("s2-h-")->id.split("-").getOrNull(2)?.toIntOrNull()
                    else->id.substringAfterLast('-').toIntOrNull()
                }?:return null
                val column=when{
                    id.startsWith("s2-p-")->2
                    id.startsWith("s2-h-")->3
                    id.startsWith("s2-return-")->4
                    else->5
                }
                val table=section(source,2)
                // Six source headings followed by 10 original six-cell digit rows.
                val row=table.drop(6).chunked(6).firstOrNull{
                    it.size==6&&it[0]==digit.toString()
                }?:return null
                label=when(column){
                    2->"အပိုင်း (၂) · +4 ပတ်သီး"
                    3->"အပိုင်း (၂) · ထိပ်စီးဇယား"
                    4->"အပိုင်း (၂) · ပျောက်ပြီးပြန်လာမူ"
                    else->"အပိုင်း (၂) · တွဲဖက်ဂဏန်း"
                }
                raw=listOf("ဂဏန်း $digit",table.getOrNull(column)?:return null,row[column])
            }
            id.startsWith("s3-")->{
                part=3;label="အပိုင်း (၃) · အုပ်စုမူ"
                val markers=when(id){
                    "s3-ten"->listOf("ပါဝါထဲမှ (16 - 94 - 83)")
                    "s3-w-010"->listOf("ပါဝါထဲမှ (50 - 27)")
                    "s3-w-011"->listOf("ညီအစ်ကိုထဲမှ (12 - 65 - 32)")
                    "s3-w-012"->listOf("ညီအစ်ကိုထဲမှ (34 - 45 - 67 - 78 - 89)")
                    "s3-w-013"->listOf("နက္ခတ်ထဲမှ (35 - 81)")
                    "s3-w-014"->listOf("အပူးအလိုက်:")
                    "s3-w-015"->listOf("ညီအစ်ကိုအလိုက်:")
                    "s3-w-016"->listOf("နက္ခတ်အလိုက်:")
                    "s3-w-017-9","s3-w-017-7"->listOf("၉ ဘရိတ် နှင့် ၇ ဘရိတ် အလိုက်:")
                    "s3-sib"->if(Regex("""နောက်ဆုံးထွက် (50|27)""").containsMatchIn(detail))
                        listOf("ပါဝါထဲမှ (50 - 27)") else listOf("ညီအစ်ကိုအလိုက်:")
                    "s3-power"->listOf("ညီအစ်ကိုထဲမှ (12 - 65 - 32)")
                    "s3-astro"->listOf("နက္ခတ်အလိုက်:")
                    "s3-double"->listOf("နက္ခတ်ထဲမှ (35 - 81)","အပူးအလိုက်:")
                    "s3-9to7","s3-7to4"->listOf("၉ ဘရိတ် နှင့် ၇ ဘရိတ် အလိုက်:")
                    "s3-week"->listOf("အပတ်စဉ် အထွက်အများဆုံး")
                    "s3-six"->listOf("၆ ကွက် ဆတိုးအုပ်စုမူ:")
                    else->emptyList()
                }
                raw=markers.mapNotNull{bullet(3,it)}
            }
            id.startsWith("s4-wheel-")||id.startsWith("s4-born-")->{
                part=4
                val double=Regex("""^s4-(?:wheel|born)-([0-9]{2})""")
                    .find(id)?.groupValues?.get(1)?:return null
                val lines=section(source,4)
                val start=lines.indexOfFirst{it.startsWith("($double) ထွက်လျှင်")}
                if(start<0||start+2>=lines.size)return null
                label="အပိုင်း (၄) · $double အပူးမွေးမူ"
                raw=lines.subList(start,start+3)
            }
            id.startsWith("s5-")->{
                part=5
                val marker=when(id){
                    "s5-mon"->"တနင်္လာနေ့ မနက် ဘရိတ်"
                    "s5-wed"->"ဗုဒ္ဓဟူးနေ့ ညနေထွက်ဂဏန်း"
                    "s5-fri"->"သောကြာနေ့ (မနက် Or ညနေ)"
                    else->return null
                }
                val lines=section(source,5)
                val start=lines.indexOfFirst{it.startsWith("*")&&marker in it}
                if(start<0)return null
                label="အပိုင်း (၅) · နေ့အလိုက်မူ"
                raw=if(id=="s5-mon")listOf(lines[start])+lines.drop(start+1)
                    .takeWhile{it.startsWith("*")}.take(10)
                    else listOf(lines[start])
            }
            id.startsWith("s6-")->{
                part=6
                val two=when(id){
                    "s6-70"->"70"
                    "s6-am92"->"92"
                    else->id.substringAfter("s6-")
                }
                if(!two.matches(Regex("[0-9]{2}")))return null
                val line=section(source,6).firstOrNull{
                    it.matches(Regex("""^\d+\..*"""))&&"($two)" in it
                }?:return null
                label="အပိုင်း (၆) · $two အလိုက်မူ"
                raw=listOf(line)
            }
            id.startsWith("s8-")->{
                part=8
                val marker=when(id){
                    "s8-80"->"80 R ထွက်လျှင်"
                    "s8-67"->"67 R ထွက်လျှင်"
                    "s8-00-bk"->"00 အပူး နှင့် ၅ ဘရိတ်မူ:"
                    "s8-48"->"48 (R) ထွက်ပြီး"
                    "s8-13"->"13 (R) ထွက်လျှင်"
                    "s8-74"->"74 (R) ထွက်လျှင်"
                    "s8-71"->"71 (R) ထွက်ပြီး"
                    "s8-15"->"15 (R) ထွက်ပြီး"
                    "s8-88"->"88 ပူးထွက်ပြီး"
                    else->return null
                }
                label="အပိုင်း (၈) · အထူးအလိုက်မူ"
                raw=listOfNotNull(bullet(8,marker))
            }
            id.startsWith("s1-930")->{
                part=1;label="အပိုင်း (၁) · 9:30 တူညီမှုမူ"
                val marker=if(id.startsWith("s1-930bk"))"ဘရိတ်တူလျှင်:" else
                    "ထိပ်တူ (သို့မဟုတ်) ပိတ်တူလျှင်:"
                raw=listOfNotNull(bullet(1,marker))
            }
            else->return null
        }
        return if(raw.isEmpty())null else
            CherryOriginalReference(part,label,raw.joinToString("\n"))
    }
}
