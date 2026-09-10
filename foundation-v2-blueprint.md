# Ledger2D Foundation V2 Blueprint

## ရည်ရွယ်ချက်

Ledger2D ကို feature များစွာပါသည့် prototype အဖြစ်သာမက မြန်မာ 2D အရောင်းသမား၏ နေ့စဉ်စာရင်းလက်ခံ၊ ဒိုင်ထံတင်၊ limit စစ်၊ ပေါက်ဂဏန်းထည့်၊ လျော်ကြေးရှင်းတမ်းလုပ်ငန်းများကို **မြန်ဆန်၊ မမှား၊ နားလည်လွယ်၊ professional** ဖြစ်အောင် လုပ်ဆောင်ပေးသည့် operational app အဖြစ် ပြန်တည်ဆောက်ရန်။

## အတည်ပြုထားသော business foundation

လက်ရှိ data concept ကို မဖျက်ပါ။

```text
User
  └── Agent / ဒိုင်
        └── Customer / ထိုဒိုင်အောက်က ထိုးသားစာရင်း
              └── BetEntry / အဲဒီဒိုင်ထံ တင်ထားသော စာရင်း
                    └── BetLine / 00–99 အကွက်တစ်ခုချင်း
```

Agent တစ်ယောက်က digit တစ်ခုအတွက် limit သတ်မှတ်လျှင် ထို Agent အောက်ရှိ Customer အားလုံး၏ ထို digit ထိုးကြေးကို draw session တစ်ကြိမ်အတွက် စုပေါင်းကန့်သတ်ရမည်။ Customer တစ်ယောက်ချင်းစီ၏ commission သည် ထို Customer ၏ ထိုးကြေး/သဘောတူညီချက်အပေါ်မူတည်၍ သီးခြားထားနိုင်ရမည်။

## User experience principle

> **Database hierarchy သည် data integrity အတွက် ဖြစ်ပြီး user workflow သည် နေ့စဉ်အလုပ်အမြန်ပြီးစီးရန် ဖြစ်သည်။ နှစ်ခုကို တိုက်ရိုက်မပေါင်းရ။**

User သည် စာရင်းတစ်ကြိမ်တင်ရန် Agent detail နှင့် Customer detail အဆင့်များကို မဖြတ်ရ။ Agent နှင့် Customer detail သည် management နှင့် history အတွက်သာ ဖြစ်ရမည်။

## Primary navigation

Bottom navigation သို့မဟုတ် persistent top-level navigation ကို အောက်ပါ ၄ ခုအဖြစ်ထားမည်။

| Tab | အဓိကအလုပ် | ပါဝင်မည့်အရာ |
|---|---|---|
| ယနေ့ | နေ့စဉ်လုပ်ငန်းစတင်ရန် | Current draw, Quick Entry, today summary, alerts |
| စာရင်း | တင်ထားသောစာရင်းကြည့်ရန် | Morning/evening, Agent filter, Customer filter, history |
| ရှင်းတမ်း | Result နှင့် ငွေစာရင်းရှင်းရန် | Winning number, payout, commission, agent settlement, net P/L |
| စီမံရန် | Master data စီမံရန် | Agents, customers, limits, closed numbers, closed days, settings |

Welcome screen သည် first launch/onboarding အတွက်သာ ဖြစ်ပြီး ပြန်ဝင်တိုင်း workflow ကို တားမထားရ။

## Home / ယနေ့ screen

Home သည် app ၏ operational control center ဖြစ်ရမည်။ အလှဆင် card များထက် လက်ရှိအလုပ်နှင့် အရေးပေါ်သတိပေးချက်ကို ဦးစားပေးရမည်။

```text
ယနေ့ 2D
ရက်စွဲ • လက်ရှိ session • ဖွင့်/ပိတ်အခြေအနေ

[အမြန်စာရင်းသွင်းရန်]

မနက် 12:00       ညနေ 4:30
စာရင်းအခြေအနေ    စာရင်းအခြေအနေ

ယနေ့အနှစ်ချုပ်
ထိုးကြေးစုစုပေါင်း • ဒိုင်အရေအတွက် • ကျန် limit သတိပေးချက်

[ယနေ့စာရင်းကြည့်ရန်] [ပေါက်ဂဏန်းထည့်ရန်]

သတိပေးချက်များ
- Limit နီးသော digit
- ပိတ်ထားသော digit
- Result မထည့်ရသေးသော session
```

Home တွင် Agent creation form ကို မထည့်ရ။ Agent ထည့်ရန်သည် `စီမံရန်` အောက်တွင်သာ ရှိရမည်။ Global actions တစ်ခုတည်းကို Home နှင့် management screen နှစ်ခုလုံးတွင် မထပ်ထားရ။

## Quick Entry workflow

Quick Entry သည် primary business action ဖြစ်သည်။ အနည်းဆုံးအဆင့်ဖြင့် အောက်ပါ flow ကို အသုံးပြုမည်။

```text
ယနေ့ / Quick Entry
  → Session အတည်ပြု
  → Agent ရွေး
  → ထို Agent အောက်က Customer ရွေး
  → Betting workspace
  → Preview + Agent-wide validation
  → Confirm
  → Receipt summary
```

### Selection rules

Agent မရွေးသေးလျှင် Customer field ကို disabled ထားရမည်။ Agent A ကိုရွေးလျှင် Agent A အောက်ရှိ Customer များပဲ query/load ပြရမည်။ Agent B အောက်က နာမည်တူ Customer ကို မရောရ။ Context header တွင် အမြဲတမ်း `ဒိုင် A • ထိုးသား A • မနက်` စသဖြင့် ပြရမည်။

### Betting workspace

Workspace တစ်ခုတည်းတွင် format၊ input၊ keyboard၊ expanded digits၊ current Agent total၊ remaining limit၊ closed status၊ commission estimate နှင့် total amount ကို ပြရမည်။ Confirm button သည် bottom sticky action ဖြစ်ရမည်။

Confirm မတိုင်မီ အနည်းဆုံး အောက်ပါအချက်များကိုမြင်ရမည်။

```text
ဒိုင် A • ထိုးသား A • မနက်
အကွက် ၆ ကွက် • စုစုပေါင်း 6,000 MMK
ကော်မရှင် ခန့်မှန်း 480 MMK
11: Agent အောက် စုစုပေါင်း 700 + ယခု 300 = 1,000 / Limit 1,000
```

## Today Ledger screen

Today Ledger သည် Agent နှင့် Customer ကို operational grouping ဖြင့်ပြမည်။

```text
ယနေ့ | မနက် | ညနေ
ဒိုင်အားလုံး | ဒိုင် A | ဒိုင် B

ဒိုင် A
  Customer A   12, 34   3,000 MMK
  Customer B   အပူး     10,000 MMK
  ဒိုင် A စုစုပေါင်း / limit alerts

ဒိုင် B
  Customer A   56       2,000 MMK
```

Agent အလိုက်စုစုပေါင်းနှင့် Customer အလိုက်အသေးစိတ်ကို ချက်ချင်းခွဲမြင်ရမည်။ နာမည်တူ Customer များကို Agent context မပါဘဲ မပြရ။

## Result & Settlement

Winning number ထည့်ခြင်းနှင့် settlement သည် ရလဒ်အလုပ်စဉ်တစ်ခုအဖြစ် ချိတ်ဆက်ရမည်။

```text
ပေါက်ဂဏန်းထည့်
  → Global result သတ်မှတ်
  → Agent တစ်ခုချင်းစီတွက်
  → Customer တစ်ယောက်ချင်းစီ payout တွက်
  → Dealer-side amount / commission
  → Player-side payout
  → User net result
  → Settlement status မှတ်တမ်းတင်
```

User net formula ကို code မရေးမီ သီးခြားအတည်ပြုရမည်။ ဒိုင်ထံမှရမည့် amount၊ Customer ကိုပေးရမည့် amount နှင့် commission ကို report တစ်ခုတည်းတွင် မရောဘဲ သီးခြား metric အဖြစ် ပြရမည်။

## Management structure

`စီမံရန်` အောက်တွင်သာ အောက်ပါအရာများထားမည်။

```text
ဒိုင်များ
  ├── ဒိုင်အသစ်ထည့်
  ├── Customer များ
  ├── လျော်ကြေးအဆ
  ├── Agent-wide digit limits
  ├── ပိတ်ဂဏန်း
  └── Agent reports

ပိတ်ရက်များ
ဘာသာစကားနှင့် ဆက်တင်များ
```

Customer-level commission သည် Customer detail မှ စီမံနိုင်သော်လည်း သက်ဆိုင်ရာ Agent context ကို အမြဲပြရမည်။

## Localization foundation

Language switching သည် screen တစ်ခုချင်းစီက hard-coded string မဟုတ်ဘဲ centralized translation keys သုံးရမည်။ နောက်ဆုံးပုံစံသည် Android `strings.xml` သို့မဟုတ် typed translation catalog တစ်ခု ဖြစ်ရမည်။

အဓိက keys များမှာ `home_title`, `today_status`, `quick_entry`, `select_agent`, `select_customer`, `confirm_bet`, `limit_exceeded`, `closed_digit`, `closed_day`, `winning_number`, `settlement`, `commission`, `payout`, `net_result`, `empty_state`, `error_state` တို့ဖြစ်သည်။ Burmese နှင့် English နှစ်ခုလုံးတွင် label၊ button၊ error၊ dialog၊ report၊ empty state အားလုံးကို တစ်ပြိုင်နက် ပြောင်းနိုင်ရမည်။

## Cherry luxury design system

Visual direction သည် dark background မဟုတ်ဘဲ light cherry luxury ဖြစ်မည်။

| Token | Direction |
|---|---|
| Background | Warm near-white / pale rose |
| Surface | White |
| Primary | Deep cherry |
| Accent | Cherry red / muted rose |
| Premium accent | Soft warm gold |
| Text | Deep charcoal |
| Success | Deep green |
| Warning | Amber |
| Error | Deep red |

Card အရေအတွက်ကို လျှော့ပြီး information hierarchy ကို typography၊ spacing၊ subtle elevation ဖြင့် ဖော်ပြရမည်။ Screen တစ်ခုတွင် primary CTA တစ်ခုသာထားရမည်။ Bottom action များသည် thumb-friendly height ရှိရမည်။ မြန်မာစာအတွက် line height၊ font scale နှင့် dynamic wrapping ကို သီးခြားစမ်းရမည်။

## Foundation implementation phases

### Phase 1 — Navigation skeleton

Home, Today Ledger, Settlement, Management top-level routes ကို တည်ဆောက်ပြီး legacy detail routes များကို management အောက်သို့ ချိတ်မည်။

### Phase 2 — Localization refactor

Hard-coded UI text အားလုံးကို centralized keys သို့ ပြောင်းမည်။ Burmese/English snapshot checklist ဖြင့် screen တစ်ခုချင်းစီ စစ်မည်။

### Phase 3 — Quick Entry

Agent dependency, customer filtering, session defaults, format workspace, Agent-wide validation နှင့် receipt summary ကို ပြန်တည်ဆောက်မည်။

### Phase 4 — Today Ledger and Settlement

Daily grouping၊ global winner၊ customer payout၊ commission၊ dealer settlement နှင့် net result ကို operational screens အဖြစ် ပြန်တည်ဆောက်မည်။

### Phase 5 — Cherry visual system

Design tokens၊ typography၊ spacing၊ surfaces၊ buttons၊ input၊ empty/loading/error states များကို တစ်နေရာတည်းက ထိန်းမည်။

### Phase 6 — Verification

အနည်းဆုံး workflow စမ်းသပ်မှုများမှာ—

1. Agent အသစ်ထည့်ပြီး Customer ထည့်ခြင်း
2. Agent A နှင့် Agent B တွင် နာမည်တူ Customer ထည့်ခြင်း
3. Agent မရွေးဘဲ Customer ရွေးရန်ကြိုးစားခြင်း
4. Agent-wide limit ပြည့်အောင် Customer အများကြီးထံမှ စာရင်းတင်ခြင်း
5. Closed digit နှင့် closed day စစ်ခြင်း
6. Morning/evening separation စစ်ခြင်း
7. Burmese/English mode အပြည့်ပြောင်းခြင်း
8. Result ထည့်ပြီး settlement ကြည့်ခြင်း
9. Receipt နှင့် history ပြန်စစ်ခြင်း
10. Large text၊ narrow screen နှင့် one-hand usage စမ်းခြင်း

## အောင်မြင်မှုသတ်မှတ်ချက်

Foundation V2 သည် အောက်ပါအချက်များ ပြည့်မီရမည်။

- ပထမဆုံးအသုံးပြုသူက လမ်းညွှန်မဖတ်ဘဲ Agent → Customer → Bet ကို အလွယ်တကူနားလည်နိုင်ရမည်။
- စာရင်းတစ်ကြိမ်တင်ရန် legacy hierarchy အဆင့်များကို မဖြတ်ရ။
- Agent မရွေးလျှင် Customer မရွေးနိုင်ရ။
- Agent A ရွေးလျှင် Agent A အောက်က Customer များပဲ ပြရမည်။
- Burmese/English mode တွင် screen တစ်ခုလုံး တစ်သမတ်တည်းဖြစ်ရမည်။
- User သည် စာရင်းမမှားမီ app က ပြောနိုင်ရမည်။
- အဓိကစာရင်းတစ်ကြိမ်ကို လက်တွေ့အသုံးပြုသူက 30 စက္ကန့်အတွင်း မှန်ကန်စွာ တင်နိုင်ရမည်။
- Home သည် daily operations အတွက်၊ Management သည် configuration အတွက် သီးခြားဖြစ်ရမည်။

## Implementation guardrail

Foundation V2 ကို အတည်မပြုမီ feature အဟောင်းများကို ထပ်မတိုးရ။ UI ကို card တစ်ခုချင်းစီလှအောင်ပြင်ခြင်းထက် navigation၊ localization၊ workflow နှင့် validation ကို ဦးစားပေးရမည်။ Data model ပြောင်းလဲမှုများသည် Agent-specific Customer ledger concept နှင့် Agent-wide limit rule ကို မချိုးဖောက်ရ။

## Current repository note

လက်ရှိ repository တွင် foundation အတွက် database နှင့် domain engine အခြေခံများ ရှိနေပြီဖြစ်သော်လည်း navigation၊ localization နှင့် daily operational screens များသည် V2 target မပြည့်သေးပါ။ ထို့ကြောင့် နောက်တစ်ဆင့် implementation သည် UI-wide refactor ဖြစ်ပြီး isolated cosmetic patch မဟုတ်ရ။
