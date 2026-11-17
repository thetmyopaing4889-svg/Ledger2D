package com.myanmar.ledger2d.core.backup

import android.database.Cursor
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter

/** Portable data-only copy of Room's user tables (not the open SQLite file/WAL). */
internal object CherryBackupDatabase {
    private val safeIdentifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private const val MAX_ROWS = 10_000_000L

    private fun identifier(s: String): String {
        require(safeIdentifier.matches(s)) { "Invalid database identifier" }
        return "\"" + s + "\""
    }
    private fun <T> Cursor.useRows(block: (Cursor) -> T): T = use { block(it) }

    private fun sqliteVersion(db: SupportSQLiteDatabase): Int =
        db.query("PRAGMA user_version").useRows {
            check(it.moveToFirst())
            it.getInt(0)
        }

    private fun columns(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA table_info(" + identifier(table) + ")").useRows { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(1))
            }
        }

    private fun parentTables(db: SupportSQLiteDatabase, table: String): List<String> =
        db.query("PRAGMA foreign_key_list(" + identifier(table) + ")").useRows { cursor ->
            buildList {
                while(cursor.moveToNext()) add(cursor.getString(2))
            }.distinct()
        }

    /** Parents precede children so FK checks hold during imports and deletes. */
    private fun orderedTables(db: SupportSQLiteDatabase): List<String> {
        val names = db.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'sqlite_%' AND name!='room_master_table' ORDER BY name"
        ).useRows { cursor ->
            buildList {
                while(cursor.moveToNext()) add(cursor.getString(0))
            }
        }
        names.forEach(::identifier)
        val nameSet = names.toSet()
        val marks = mutableMapOf<String, Int>()
        val sorted = mutableListOf<String>()
        fun visit(table: String) {
            when(marks[table]) {
                2 -> return
                1 -> error("Unsupported cyclic foreign keys in database")
            }
            marks[table] = 1
            parentTables(db, table).filter { it in nameSet }.sorted().forEach(::visit)
            marks[table] = 2
            sorted.add(table)
        }
        names.forEach(::visit)
        return sorted
    }

    fun write(db: SupportSQLiteDatabase, output: OutputStream): Long {
        val writer = JsonWriter(OutputStreamWriter(output, Charsets.UTF_8))
        val tables = orderedTables(db)
        var total = 0L
        writer.beginObject()
        writer.name("database_version").value(sqliteVersion(db))
        writer.name("tables").beginArray()
        for(table in tables) {
            val names=columns(db,table)
            writer.beginObject()
            writer.name("table").value(table)
            writer.name("columns").beginArray()
            names.forEach(writer::value)
            writer.endArray()
            writer.name("rows").beginArray()
            db.query("SELECT * FROM " + identifier(table)).useRows { cursor ->
                require(cursor.columnCount == names.size)
                while(cursor.moveToNext()) {
                    total++
                    require(total <= MAX_ROWS) { "Backup row count exceeds safety limit" }
                    writer.beginArray()
                    for (column in names.indices) {
                        val type=cursor.getType(column)
                        if(type == Cursor.FIELD_TYPE_NULL) {
                            writer.nullValue()
                        } else {
                            writer.beginArray()
                            when(type) {
                                Cursor.FIELD_TYPE_INTEGER -> {
                                    writer.value("L")
                                    writer.value(cursor.getLong(column).toString())
                                }
                                Cursor.FIELD_TYPE_FLOAT -> {
                                    writer.value("D")
                                    writer.value(cursor.getDouble(column).toString())
                                }
                                Cursor.FIELD_TYPE_STRING -> {
                                    writer.value("S")
                                    writer.value(cursor.getString(column))
                                }
                                Cursor.FIELD_TYPE_BLOB -> {
                                    writer.value("B")
                                    writer.value(Base64.encodeToString(cursor.getBlob(column),Base64.NO_WRAP))
                                }
                                else -> error("Unsupported SQLite field type")
                            }
                            writer.endArray()
                        }
                    }
                    writer.endArray()
                }
            }
            writer.endArray()
            writer.endObject()
        }
        writer.endArray()
        writer.endObject()
        writer.flush()
        return total
    }

    private data class Cell(val type: String, val value: String)
    private fun read(
        db: SupportSQLiteDatabase,
        input: InputStream,
        onTable: (String,List<String>) -> Unit,
        onRow: (String,List<Cell?>) -> Unit,
    ): Long {
        val expected = orderedTables(db)
        val reader = JsonReader(InputStreamReader(input, Charsets.UTF_8))
        reader.isLenient = false
        reader.beginObject()
        require(reader.nextName()=="database_version")
        require(reader.nextInt()==sqliteVersion(db)) { "Backup Room schema version mismatch" }
        require(reader.nextName()=="tables")
        reader.beginArray()
        var total=0L
        for (table in expected) {
            require(reader.hasNext()) { "Missing database table in backup" }
            reader.beginObject()
            require(reader.nextName()=="table")
            require(reader.nextString()==table) { "Unexpected database table in backup" }
            require(reader.nextName()=="columns")
            reader.beginArray()
            val archivedColumns = buildList {
                while(reader.hasNext()) add(reader.nextString())
            }
            reader.endArray()
            require(archivedColumns == columns(db,table)) {
                "Incompatible column layout in " + table
            }
            require(reader.nextName()=="rows")
            onTable(table,archivedColumns)
            reader.beginArray()
            while(reader.hasNext()) {
                reader.beginArray()
                val cells=ArrayList<Cell?>(archivedColumns.size)
                for (column in archivedColumns) {
                    if(reader.peek()==JsonToken.NULL) {
                        reader.nextNull()
                        cells.add(null)
                    } else {
                        reader.beginArray()
                        val type = reader.nextString()
                        require(type in setOf("L","D","S","B")) { "Unknown backup cell type" }
                        val value = reader.nextString()
                        require(!reader.hasNext())
                        reader.endArray()
                        // Decode values during preflight as well, before any writes.
                        when(type) {
                            "L" -> value.toLong()
                            "D" -> require(value.toDouble().isFinite())
                            "B" -> {
                                require(value.length <= 64 * 1024 * 1024)
                                Base64.decode(value,Base64.DEFAULT)
                            }
                        }
                        cells.add(Cell(type,value))
                    }
                }
                require(!reader.hasNext()) { "Extra column in archived row" }
                reader.endArray()
                onRow(table,cells)
                total++
                require(total <= MAX_ROWS)
            }
            reader.endArray()
            require(!reader.hasNext())
            reader.endObject()
        }
        require(!reader.hasNext()) { "Additional unexpected database tables" }
        reader.endArray()
        require(!reader.hasNext()) { "Additional database metadata" }
        reader.endObject()
        require(reader.peek()==JsonToken.END_DOCUMENT) { "Trailing backup database bytes" }
        return total
    }

    /** Full parse + FK schema match without modifying live data. */
    fun inspect(db: SupportSQLiteDatabase, input: InputStream): Long =
        read(db,input,{ _,_ -> },{ _,_ -> })

    /** Caller owns one transaction and a verified backup; any exception rolls it back. */
    fun importInsideTransaction(db: SupportSQLiteDatabase, input: InputStream) {
        val order = orderedTables(db)
        order.asReversed().forEach { db.execSQL("DELETE FROM " + identifier(it)) }
        val statementByTable=mutableMapOf<String,SupportSQLiteStatement>()
        try {
            read(db,input,{table,names->
                val namesSql=names.joinToString(","){identifier(it)}
                val placeholders=names.joinToString(","){"?"}
                statementByTable[table] = db.compileStatement(
                    "INSERT INTO " + identifier(table) + " (" + namesSql + ") VALUES (" + placeholders + ")"
                )
            },{table,cells->
                val insert = requireNotNull(statementByTable[table])
                insert.clearBindings()
                cells.forEachIndexed { i,cell ->
                    val position=i+1
                    when(cell?.type) {
                        null -> insert.bindNull(position)
                        "L" -> insert.bindLong(position,cell.value.toLong())
                        "D" -> insert.bindDouble(position,cell.value.toDouble())
                        "S" -> insert.bindString(position,cell.value)
                        "B" -> insert.bindBlob(position,Base64.decode(cell.value,Base64.DEFAULT))
                    }
                }
                insert.executeInsert()
            })
            db.query("PRAGMA foreign_key_check").useRows { cursor ->
                check(!cursor.moveToFirst()) { "Restored data violates foreign keys" }
            }
        } finally {
            statementByTable.values.forEach { runCatching { it.close() } }
        }
    }
}
