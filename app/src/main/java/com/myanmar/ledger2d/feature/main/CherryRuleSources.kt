package com.myanmar.ledger2d.feature.main

/** Source excerpts always come from the bundled unmodified rules text. */
internal data class CherryOriginalReference(
    val section: Int,
    val label: String,
    val quote: String,
)

internal object CherryRuleSources {
    private val digits = listOf("၀","၁","၂","၃","၄","၅","၆","၇","၈","၉")
    private val heading = Regex("""^အပိုင်း \([၁၂၃၄၅၆၇၈၉]\)""")

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
        val part:Int
        val label:String
        val raw:List<String>
        fun bullet(num:Int,term:String)=section(source,num)
            .firstOrNull{it.startsWith("*")&&term in it}

        when {
            id.startsWith("s2-p-")||id.startsWith("s2-h-")||
                id.startsWith("s2-f-")||id=="s2-8head"->{
                part=2
                val digit=when{
                    id=="s2-8head"->8
                    id.startsWith("s2-h-")->id.split("-").getOrNull(2)?.toIntOrNull()
                    else->id.substringAfterLast('-').toIntOrNull()
                }?:return null
                val column=when{
                    id.startsWith("s2-p-")->2
                    id.startsWith("s2-h-")->3
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
                    else->"အပိုင်း (၂) · တွဲဖက်ဂဏန်း"
                }
                raw=listOf("ဂဏန်း $digit",table.getOrNull(column)?:return null,row[column])
            }
            id.startsWith("s3-")->{
                part=3;label="အပိုင်း (၃) · အုပ်စုမူ"
                val markers=when(id){
                    "s3-ten"->listOf("ပါဝါထဲမှ (16 - 94 - 83)")
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
                val marker=if(id=="s1-930bk")"ဘရိတ်တူလျှင်:" else
                    "ထိပ်တူ (သို့မဟုတ်) ပိတ်တူလျှင်:"
                raw=listOfNotNull(bullet(1,marker))
            }
            else->return null
        }
        return if(raw.isEmpty())null else
            CherryOriginalReference(part,label,raw.joinToString("\n"))
    }
}
