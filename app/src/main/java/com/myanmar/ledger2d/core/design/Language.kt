package com.myanmar.ledger2d.core.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

class LanguageState(context: Context, private val workingContext: WorkingContextStore) {
    private val preferences = context.getSharedPreferences("ledger_settings", Context.MODE_PRIVATE)

    init {
        // Legacy releases stored this UI-only state. It must never be restored into a new app session.
        preferences.edit().remove("selected_date").remove("selected_session").apply()
    }

    var code by mutableStateOf(preferences.getString("language", "my") ?: "my")
        private set
    var defaultAgentId by mutableStateOf(preferences.getLong("default_agent_id", 0L))
        private set
    var defaultCustomerId by mutableStateOf(preferences.getLong("default_customer_id", 0L))
        private set
    var onboardingComplete by mutableStateOf(preferences.getBoolean("onboarding_complete", false))
        private set

    val selectedDate: String
        get() = workingContext.current.date.toString()
    val selectedSession: DrawSession
        get() = workingContext.current.session
    val workingContextReady: Boolean
        get() = workingContext.isInitialized

    fun initializeWorkingContext(default: DrawIdentity) = workingContext.initialize(default)
    fun set(code: String) { this.code = code; preferences.edit().putString("language", code).apply() }
    fun setDefaultAgent(id: Long) { defaultAgentId = id; defaultCustomerId = 0L; preferences.edit().putLong("default_agent_id", id).putLong("default_customer_id", 0L).apply() }
    fun setDefaultCustomer(id: Long) { defaultCustomerId = id; preferences.edit().putLong("default_customer_id", id).apply() }
    fun completeOnboarding() { onboardingComplete = true; preferences.edit().putBoolean("onboarding_complete", true).apply() }
    fun setDate(value: String) { workingContext.setDate(LocalDate.parse(value)) }
    fun setSession(value: DrawSession) { workingContext.setSession(value) }
    fun text(my: String, en: String): String = if (code == "en") en else my
    fun translate(value: String): String = if (code == "my") value else english[value] ?: value
    private val english = mapOf(
        "ဒိုင်များ" to "Agents", "ဒိုင်အသစ်ထည့်ရန်" to "Add agent", "စာရင်း" to "Ledger", "Cherry 2D" to "Cherry 2D", "ထိုးသားများ" to "Customers", "ထိုးသားအသစ်ထည့်ရန်" to "Add customer",
        "ဒိုင်အချက်အလက်ပြင်ရန်" to "Edit agent", "ထိုးသားအချက်အလက်ပြင်ရန်" to "Edit customer", "အမည်" to "Name", "လိပ်စာ" to "Address", "ဖုန်း" to "Phone", "မှတ်ချက်" to "Remark", "အကွက်စာရင်း" to "Digit list", "ကော်မရှင်နှုန်းထား" to "Commission", "နှုန်းထား" to "Rate", "အကွက်စာရင်း" to "Digit list", "ကော်မရှင်နှုန်းထား" to "Commission", "နှုန်းထား" to "Rate",
         "ဆက်တင်များ" to "Settings", "ဘာသာစကား" to "Language", "ပိတ်ရက်" to "Closed days", "ထီပေါက်စဉ်" to "2D results",
        "အစီရင်ခံစာ" to "Report", "သုံးသပ်ချက်" to "Analysis", "စုစုပေါင်းစာရင်း" to "Total list", "ပိတ်ဂဏန်း" to "Closed numbers", "ကန့်သတ်ပမာဏ" to "Limits", "လမ်းညွှန်" to "Format",
        "စာရင်းသွင်းရန်" to "New entry", "စာရင်းမှတ်တမ်း" to "History", "အမြန်သုံးသပ်ချက်" to "Analysis", "အတည်ပြုမည်" to "Confirm", "သိမ်းမည်" to "Save", "မလုပ်တော့ပါ" to "Cancel", "ဖယ်ရှားမည်" to "Remove", "ပြင်မည်" to "Edit",
        "ရက်စွဲ" to "Date", "ထိုးမည့်အချိန်" to "Draw", "အမြန်ထည့်သွင်းပုံများ" to "Quick format", "အကြိုကြည့်ရှုရန်" to "Preview", "မနက်" to "Morning", "ညနေ" to "Evening", "ယနေ့စာရင်း" to "Today ledger", "ရှင်းတမ်း" to "Settlement", "ဒိုင်မရှိသေးပါ" to "No agents yet", "ဒိုင်ထည့်ပြီးမှ ယနေ့စာရင်းကို ကြည့်နိုင်ပါမည်" to "Add an agent to view today's ledger", "စာရင်းမရှိသေးပါ" to "No entries yet", "စုစုပေါင်း" to "Total", "ဒိုင်ထည့်ပြီးမှ ရှင်းတမ်းတွက်နိုင်ပါမည်" to "Add an agent to calculate settlement", "ထိုးကြေး" to "Stake", "ပေါက်ကြေး" to "Winning stake", "လျော်ပေးငွေ" to "Payout", "ကော်မရှင်" to "Commission", "ရှင်းတမ်းအတည်ပြုပြီး" to "Settlement confirmed", "ရှင်းတမ်းအတည်ပြုမည်" to "Confirm settlement",
        "ဂဏန်းမထွက်ခင်" to "Before", "ဂဏန်းထွက်ပြီးချိန်" to "After", "အပတ်စဉ်" to "Weekly", "ရှုံး/မြတ်" to "P/L", "ပမာဏ" to "Amount", "ရာခိုင်နှုန်း" to "Percentage", "ယနေ့အလုပ်ခွင်" to "Today's workspace", "မင်္ဂလာပါ" to "Good morning", "ယနေ့စာရင်းကို အမြန်စီမံပါ" to "Manage today's entries quickly", "ယနေ့ ပိတ်ရက်ဖြစ်သည်" to "Today is closed", "စာရင်းအသစ် လက်မခံပါ" to "New entries are not accepted", "ယနေ့ 2D အလုပ်အခြေအနေ" to "Today's 2D status", "မနက်နှင့် ညနေစာရင်းများကို တစ်နေရာတည်းမှ စီမံပါ" to "Manage morning and evening entries in one place", "ဒိုင်" to "Agents", "နေ့" to "Day", "ပိတ်" to "Closed", "ဖွင့်" to "Open", "အမြန်စာရင်းသွင်းရန်" to "Quick entry", "အမြန်လုပ်ဆောင်ရန်" to "Quick actions", "ဒိုင်များ" to "Agents", "ပေါက်ဂဏန်း" to "Winning number", "ဒိုင်နှင့် ထိုးသားကို ရွေးပြီး စာရင်းတင်ပါ" to "Select an agent and customer to submit a bet", "အရင် ဒိုင်ကိုရွေးပါ။ ရွေးထားသောဒိုင်အောက်က ထိုးသားများပဲ ပြပါမည်။" to "Select an agent first. Only customers under that agent will be shown.", "ဒိုင်ရွေးပါ" to "Select agent", "ဒိုင်မရွေးရသေးပါ" to "No agent selected", "ထိုးသားရွေးပါ" to "Select customer", "ဒိုင်ရွေးပြီးမှ ထိုးသားရွေးပါ" to "Select an agent before choosing a customer", "အကွက်နှင့် ထိုးကြေးထည့်ရန်" to "Enter digits and stake", "ပထမဆုံး ဒိုင်ကိုထည့်ပြီး စတင်ပါ" to "Add your first agent to begin"
    ) + mapOf(
        "ကြိုဆိုပါသည်" to "Welcome to", "Cherry 2D စာရင်း" to "Cherry 2D Ledger", "ယခုအပတ် ထွက်ဂဏန်းများ" to "This week's results",
        "မနက် / ညနေ" to "Morning / Evening", "ဒိုင်အသစ်ထည့်ရန်" to "Add agent", "ထိုးသားအသစ်ထည့်ရန်" to "Add customer",
        "ဒိုင်ရွေးရန်" to "Select agent", "ထိုးသားရွေးရန်" to "Select customer", "အချက်အလက်" to "Information",
        "ပိတ်မည်" to "Close", "ဖျက်မည်" to "Delete", "ရွေးမည်" to "Select", "ရှင်းမည်" to "Clear", "ထပ်ထည့်မည်" to "Add another",
        "မရှိသေးပါ" to "None yet", "ထိုးသားမရှိသေးပါ" to "No customers yet", "ရလဒ်" to "Results", "ယနေ့" to "Today", "အပတ်စဉ် ရလဒ်အချက်အလက်" to "Weekly results", "ဒိုင်အသစ်" to "New agent", "ထိုးသားအသစ်" to "New customer", "လာမည်" to "Soon", "အသိပေးချက်" to "Notifications", "အမြန်" to "Quick",
        "ထိုးသား" to "Customer", "ဒိုင်" to "Agent", "အဆင်ပြေ" to "OK", "အတည်ပြုနေသည်…" to "Confirming…", "သိမ်းပြီးပါပြီ" to "Saved",
        "Agent Dashboard" to "Agent Dashboard", "Customer Dashboard" to "Customer Dashboard", "Agent feature များ" to "Agent features", "Customer feature များ" to "Customer features",
        "လုပ်ဆောင်ချက်တစ်ခုကို ရွေးပြီးမှ Agent ရွေးပါ" to "Choose a feature, then select an agent", "Agent ကိုအရင်ရွေးပြီးမှ Customer feature ကို အသုံးပြုပါ" to "Select an agent before using customer features",
        "Agent List" to "Agent List", "Customer List" to "Customer List", "ဒိုင်အားလုံး" to "All agents", "Customer အားလုံး" to "All customers",
        "ဆက်သွားရန်" to "Continue", "Agent ရွေးရန်" to "Select agent", "Customer ရွေးရန်" to "Select customer", "ဒိုင်ရွေးရန်" to "Select agent",
        "ဒိုင်ရွေးပြီးမှ ရွေးပါ" to "Select an agent first", "ရွေးထားသည်" to "Selected", "Agent တစ်ယောက် သို့မဟုတ် ဒိုင်အားလုံးကို ရွေးပါ" to "Select an agent or all agents",
        "Agent တစ်ယောက်နှင့် Customer အားလုံး သို့မဟုတ် Customer တစ်ယောက်ကို ရွေးပါ" to "Select an agent and all customers or one customer",
        "Add Agent နဲ့ ဖန်တီးထားသော Agent များ" to "Agents created with Add Agent", "Home မှ ဒိုင်အသစ်ထည့်ရန်ကို အသုံးပြုပါ" to "Use Add agent from Home",
        "ကြည့်ရန်" to "View", "Agent ကိုအရင်ရွေးပါ" to "Select an agent first", "အောက်ရှိ Customer များ" to "customers under this agent",
        "ဒီ Agent အောက်မှာ Customer ထည့်ပါ" to "Add a customer under this agent", "Customer မရှိသေးပါ" to "No customers yet",
        "Customer List ကြည့်ရန် Agent တစ်ယောက်ကို ရွေးပါ" to "Select an agent to view the customer list", "Agent Information" to "Agent Information",
        "Customer Information" to "Customer Information", "ပြင်ရန်" to "Edit", "စာရင်း" to "entries", "ပြီး" to "Settled",
        "Net settlement" to "Net settlement", "ပေါက်ဂဏန်းမရှိသေးပါ" to "No winning number yet",
        "ရက်စွဲကို စစ်ဆေးပါ" to "Check the date", "ထီပေါက်ဂဏန်း မရှိသေးပါ" to "No winning number yet",
        "ထီပေါက်ပြီးချိန်အတွက် ရလဒ်မရှိသေးပါ" to "No result is available after the draw yet",
        "အချက်အလက် မရှိသေးပါ" to "No information yet", "စာရင်းသွင်း၍ မရပါ။ အချက်အလက်နှင့် ကန့်သတ်ချက်များကို ပြန်စစ်ပါ။" to "Cannot submit. Check the details and limits.",
        "စာရင်းပြင်၍ မရပါ။ အချက်အလက်နှင့် ကန့်သတ်ချက်များကို ပြန်စစ်ပါ။" to "Cannot edit. Check the details and limits.",
        "အတည်ပြုထားသော စာရင်းမရှိသေးပါ" to "No confirmed entries yet", "ဒိုင်အလိုက် ကန့်သတ်ပမာဏ" to "Agent limits",
        "အထူးကန့်သတ်ချက်ဖျက်မည်လား" to "Delete special limit?", "ကို ဒီဒိုင်အောက်ရှိ ထိုးသားအားလုံးအတွက် ဖျက်မည်လား?" to "for all customers under this agent?",
        "မလုပ်ပါ" to "No", "ဒီဒိုင်အောက်က ထိုးသားအားလုံး၏ digit စုစုပေါင်း limit" to "Total digit limit for all customers under this agent",
        "အကွက်အားလုံးအတွက် limit" to "Limit for all digits", "ရွေးချယ်ထားသော digit အတွက် limit" to "Limit for selected digit",
        "ဂဏန်း" to "Digit", "ပမာဏ" to "Amount", "ဖယ်ရှားမည်" to "Remove",
        "ထည့်သွင်းရန် လိုအပ်ပါသည်" to "Input is required", "ငွေပမာဏကို အပေါင်းကိန်းပြည့်ဖြင့် ထည့်ပါ" to "Enter a positive whole-number amount",
        "ဂဏန်းကို 00 မှ 99 အတွင်း ထည့်ပါ" to "Enter a two-digit number from 00 to 99", "ဂဏန်းနှင့် ငွေပမာဏ ထည့်ပါ" to "Enter a digit and amount",
        "ပိတ်ရက်ဖြစ်သောကြောင့် စာရင်းသွင်း၍ မရပါ" to "Cannot submit on a closed day",
        "Each entry must contain a digit and amount" to "Each entry must contain a digit and amount",
        "Each entry must contain a valid format" to "Each entry must contain a valid format"
    )
}
val LocalLanguage = staticCompositionLocalOf<LanguageState> { error("LanguageState not provided") }
@Composable fun rememberLanguageState(context: Context, workingContext: WorkingContextStore): LanguageState = remember(workingContext) { LanguageState(context, workingContext) }
