package com.myanmar.ledger2d.core.database

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Exercise the real RoomOpenHelper v11->v12 path with existing ledger records.
 * This uses a v12 schema without the additive v12 table as a v11 fixture. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],manifest=Config.NONE,application=Application::class)
class CommissionMigrationTest {
    @Test fun migratingExistingLedgerAddsOnlyCommissionRulesAndRetainsOtherTables()=runBlocking {
        val context=RuntimeEnvironment.getApplication<Application>()
        val name="ledger2d.db"
        context.deleteDatabase(name)
        try {
            val initial=LedgerDatabase.create(context)
            val a=initial.agentDao().upsert(AgentEntity(name="Agent A",rate=80,createdAt=1,updatedAt=1))
            val c=initial.customerDao().upsert(CustomerEntity(agentId=a,name="Customer A",
                commissionRateBasisPoints=1500,createdAt=1,updatedAt=1))
            initial.close()
            // Reconstitute the immediately preceding schema version; existing
            // agents/customers and the LIVE/History tables are left untouched.
            val raw=SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath,
                null,SQLiteDatabase.OPEN_READWRITE)
            raw.execSQL("DROP TABLE customer_commission_rules")
            raw.version=11
            raw.close()

            val upgraded=LedgerDatabase.create(context)
            try {
                assertEquals(80L,upgraded.agentDao().get(a)!!.rate)
                assertEquals(1500,upgraded.customerDao().get(c)!!.commissionRateBasisPoints)
                val tables=mutableSetOf<String>()
                upgraded.openHelper.readableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type='table'").use { rows ->
                    while(rows.moveToNext())tables.add(rows.getString(0))
                }
                assertTrue("Commission rules newly created", "customer_commission_rules" in tables)
                assertTrue("History preserved","history_results" in tables)
                assertTrue("LIVE preserved","live_daily_results" in tables)
                assertTrue("Winning number preserved","winning_numbers" in tables)
                assertTrue("Bets preserved","bet_entries" in tables)
            } finally {upgraded.close()}
        } finally {context.deleteDatabase(name)}
    }
}
