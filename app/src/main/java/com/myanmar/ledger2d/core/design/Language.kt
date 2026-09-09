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
        "ဒိုင်များ" to "Agents", "ဒိုင်အသစ်ထည့်ရန်" to "Add agent", "ထိုးသားများ" to "Customers", "ထိုးသားအသစ်ထည့်ရန်" to "Add customer",
        "ဒိုင်အချက်အလက်ပြင်ရန်" to "Edit agent", "ထိုးသားအချက်အလက်ပြင်ရန်" to "Edit customer", "အမည်" to "Name", "လိပ်စာ" to "Address", "ဖုန်း" to "Phone", "မှတ်ချက်" to "Remark",
        "လျော်ကြေးနှုန်းထား" to "Rate", "ဆက်တင်များ" to "Settings", "ဘာသာစကား" to "Language", "ပိတ်ရက်" to "Closed days", "ထီပေါက်စဉ်" to "2D results",
        "အစီရင်ခံစာ" to "Report", "သုံးသပ်ချက်" to "Analysis", "စုစုပေါင်းစာရင်း" to "Total list", "ပိတ်ဂဏန်း" to "Closed numbers", "ကန့်သတ်ပမာဏ" to "Limits", "လမ်းညွှန်" to "Format",
        "စာရင်းသွင်းရန်" to "New entry", "စာရင်းမှတ်တမ်း" to "History", "အမြန်သုံးသပ်ချက်" to "Analysis", "အတည်ပြုမည်" to "Confirm", "သိမ်းမည်" to "Save", "မလုပ်တော့ပါ" to "Cancel", "ဖယ်ရှားမည်" to "Remove", "ပြင်မည်" to "Edit",
        "ရက်စွဲ" to "Date", "ထိုးမည့်အချိန်" to "Draw", "အမြန်ထည့်သွင်းပုံများ" to "Quick format", "အကြိုကြည့်ရှုရန်" to "Preview", "မနက်" to "Morning", "ညနေ" to "Evening",
        "ဂဏန်းမထွက်ခင်" to "Before", "ဂဏန်းထွက်ပြီးချိန်" to "After", "အပတ်စဉ်" to "Weekly", "ရှုံး/မြတ်" to "P/L", "ပမာဏ" to "Amount", "ရာခိုင်နှုန်း" to "Percentage"
    )
}
val LocalLanguage = staticCompositionLocalOf<LanguageState> { error("LanguageState not provided") }
@Composable fun rememberLanguageState(context: Context): LanguageState = remember { LanguageState(context) }
