package com.myanmar.ledger2d.core.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

class LanguageState(context: Context) {
    private val preferences = context.getSharedPreferences("ledger_settings", Context.MODE_PRIVATE)
    var code by mutableStateOf(preferences.getString("language", "my") ?: "my")
        private set
    fun set(code: String) { this.code = code; preferences.edit().putString("language", code).apply() }
    fun text(my: String, en: String): String = if (code == "en") en else my
    fun translate(value: String): String = if (code == "my") value else english[value] ?: value
    private val english = mapOf(
        "ဒိုင်များ" to "Agents", "ဒိုင်အသစ်ထည့်ရန်" to "Add agent", "စာရင်း" to "Ledger", "မြန်မာ 2D" to "Myanmar 2D", "စာရင်း" to "Ledger", "မြန်မာ 2D" to "Myanmar 2D", "ထိုးသားများ" to "Customers", "ထိုးသားအသစ်ထည့်ရန်" to "Add customer",
        "ဒိုင်အချက်အလက်ပြင်ရန်" to "Edit agent", "ထိုးသားအချက်အလက်ပြင်ရန်" to "Edit customer", "အမည်" to "Name", "လိပ်စာ" to "Address", "ဖုန်း" to "Phone", "မှတ်ချက်" to "Remark", "အကွက်စာရင်း" to "Digit list", "ကော်မရှင်နှုန်းထား" to "Commission", "နှုန်းထား" to "Rate", "အကွက်စာရင်း" to "Digit list", "ကော်မရှင်နှုန်းထား" to "Commission", "နှုန်းထား" to "Rate",
         "ဆက်တင်များ" to "Settings", "ဘာသာစကား" to "Language", "ပိတ်ရက်" to "Closed days", "ထီပေါက်စဉ်" to "2D results",
        "အစီရင်ခံစာ" to "Report", "သုံးသပ်ချက်" to "Analysis", "စုစုပေါင်းစာရင်း" to "Total list", "ပိတ်ဂဏန်း" to "Closed numbers", "ကန့်သတ်ပမာဏ" to "Limits", "လမ်းညွှန်" to "Format",
        "စာရင်းသွင်းရန်" to "New entry", "စာရင်းမှတ်တမ်း" to "History", "အမြန်သုံးသပ်ချက်" to "Analysis", "အတည်ပြုမည်" to "Confirm", "သိမ်းမည်" to "Save", "မလုပ်တော့ပါ" to "Cancel", "ဖယ်ရှားမည်" to "Remove", "ပြင်မည်" to "Edit",
        "ရက်စွဲ" to "Date", "ထိုးမည့်အချိန်" to "Draw", "အမြန်ထည့်သွင်းပုံများ" to "Quick format", "အကြိုကြည့်ရှုရန်" to "Preview", "မနက်" to "Morning", "ညနေ" to "Evening",
        "ဂဏန်းမထွက်ခင်" to "Before", "ဂဏန်းထွက်ပြီးချိန်" to "After", "အပတ်စဉ်" to "Weekly", "ရှုံး/မြတ်" to "P/L", "ပမာဏ" to "Amount", "ရာခိုင်နှုန်း" to "Percentage", "ယနေ့အလုပ်ခွင်" to "Today's workspace", "မင်္ဂလာပါ" to "Good morning", "ယနေ့စာရင်းကို အမြန်စီမံပါ" to "Manage today's entries quickly", "ယနေ့ ပိတ်ရက်ဖြစ်သည်" to "Today is closed", "စာရင်းအသစ် လက်မခံပါ" to "New entries are not accepted", "ယနေ့ 2D အလုပ်အခြေအနေ" to "Today's 2D status", "မနက်နှင့် ညနေစာရင်းများကို တစ်နေရာတည်းမှ စီမံပါ" to "Manage morning and evening entries in one place", "ဒိုင်" to "Agents", "နေ့" to "Day", "ပိတ်" to "Closed", "ဖွင့်" to "Open", "အမြန်စာရင်းသွင်းရန်" to "Quick entry", "အမြန်လုပ်ဆောင်ရန်" to "Quick actions", "ဒိုင်များ" to "Agents", "ပေါက်ဂဏန်း" to "Winning number", "ဒိုင်နှင့် ထိုးသားကို ရွေးပြီး စာရင်းတင်ပါ" to "Select an agent and customer to submit a bet", "အရင် ဒိုင်ကိုရွေးပါ။ ရွေးထားသောဒိုင်အောက်က ထိုးသားများပဲ ပြပါမည်။" to "Select an agent first. Only customers under that agent will be shown.", "ဒိုင်ရွေးပါ" to "Select agent", "ဒိုင်မရွေးရသေးပါ" to "No agent selected", "ထိုးသားရွေးပါ" to "Select customer", "ဒိုင်ရွေးပြီးမှ ထိုးသားရွေးပါ" to "Select an agent before choosing a customer", "အကွက်နှင့် ထိုးကြေးထည့်ရန်" to "Enter digits and stake", "ပထမဆုံး ဒိုင်ကိုထည့်ပြီး စတင်ပါ" to "Add your first agent to begin"
    )
}
val LocalLanguage = staticCompositionLocalOf<LanguageState> { error("LanguageState not provided") }
@Composable fun rememberLanguageState(context: Context): LanguageState = remember { LanguageState(context) }
