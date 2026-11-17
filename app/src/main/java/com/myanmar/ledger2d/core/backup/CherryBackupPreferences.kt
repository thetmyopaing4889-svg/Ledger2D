package com.myanmar.ledger2d.core.backup

import android.content.Context
import android.util.JsonWriter
import org.json.JSONObject
import org.json.JSONArray
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Snapshot all application SharedPreferences, not just the Room database. */
internal class CherryBackupPreferences(private val context: Context) {
    private val prefsDir = java.io.File(context.applicationInfo.dataDir, "shared_prefs")
    private val safeName = Regex("[A-Za-z0-9_.-]{1,140}")

    private fun listNames(): List<String> =
        prefsDir.listFiles().orEmpty().asSequence()
            .filter { it.isFile && it.name.endsWith(".xml") }
            .map { it.name.removeSuffix(".xml") }
            .filter { safeName.matches(it) }
            .distinct().sorted().toList()

    private fun current(name: String): Map<String, Any> {
        require(safeName.matches(name))
        return context.getSharedPreferences(name,Context.MODE_PRIVATE).all
            .mapNotNull { (key,value) -> value?.let { key to it } }.toMap()
    }

    fun snapshot(): Map<String,Map<String,Any>> = listNames().associateWith(::current)

    private fun writerValue(writer: JsonWriter, value: Any) {
        when(value) {
            is String -> { writer.name("type").value("s");writer.name("value").value(value) }
            is Boolean -> { writer.name("type").value("b");writer.name("value").value(value) }
            is Int -> { writer.name("type").value("i");writer.name("value").value(value.toString()) }
            is Long -> { writer.name("type").value("l");writer.name("value").value(value.toString()) }
            is Float -> { writer.name("type").value("f");writer.name("value").value(value.toString()) }
            is Set<*> -> {
                val strings=value.map {
                    require(it is String) { "Unsupported preference set value" }
                    it
                }.sorted()
                writer.name("type").value("ss")
                writer.name("value").beginArray()
                strings.forEach(writer::value)
                writer.endArray()
            }
            else -> throw IllegalStateException("Unsupported preference type: " + value.javaClass.name)
        }
    }

    fun write(output: OutputStream, state: Map<String,Map<String,Any>> = snapshot()) {
        val writer=JsonWriter(OutputStreamWriter(output,Charsets.UTF_8))
        writer.beginObject()
        writer.name("version").value(1)
        writer.name("files").beginArray()
        for((name,entries) in state.toSortedMap()) {
            require(safeName.matches(name))
            writer.beginObject()
            writer.name("name").value(name)
            writer.name("entries").beginArray()
            for((key,value) in entries.toSortedMap()) {
                writer.beginObject()
                writer.name("key").value(key)
                writerValue(writer,value)
                writer.endObject()
            }
            writer.endArray()
            writer.endObject()
        }
        writer.endArray()
        writer.endObject()
        writer.flush()
    }

    private fun readBounded(input: InputStream): ByteArray {
        val out=ByteArrayOutputStream()
        val buffer=ByteArray(8192)
        var n=0L
        while(true) {
            val count=input.read(buffer)
            if(count<0) break
            n+=count
            if(n>128L*1024*1024) throw IOException("Preferences backup is too large")
            out.write(buffer,0,count)
        }
        return out.toByteArray()
    }

    /** Verify every pref entry and type *before* changing the live application. */
    fun parse(input: InputStream): Map<String,Map<String,Any>> {
        val root=JSONObject(String(readBounded(input),Charsets.UTF_8))
        require(root.getInt("version")==1)
        val files=root.getJSONArray("files")
        require(files.length()<=1000)
        val data=linkedMapOf<String,Map<String,Any>>()
        for(i in 0 until files.length()) {
            val group=files.getJSONObject(i)
            val name=group.getString("name")
            require(safeName.matches(name) && name !in data)
            val entries=group.getJSONArray("entries")
            require(entries.length()<=1_000_000)
            val saved=linkedMapOf<String,Any>()
            for(j in 0 until entries.length()) {
                val item=entries.getJSONObject(j)
                val key=item.getString("key")
                require(key.isNotBlank() && key !in saved)
                val value: Any = when(item.getString("type")) {
                    "s" -> item.getString("value")
                    "b" -> item.getBoolean("value")
                    "i" -> item.getString("value").toInt()
                    "l" -> item.getString("value").toLong()
                    "f" -> item.getString("value").toFloat().also { require(it.isFinite()) }
                    "ss" -> {
                        val array=item.getJSONArray("value")
                        require(array.length()<=1_000_000)
                        (0 until array.length()).map { array.getString(it) }.toSet()
                    }
                    else -> error("Unsupported backup preference type")
                }
                saved[key]=value
            }
            data[name]=saved
        }
        return data
    }

    /** One XML file is atomic; caller also owns Room txn + recovery journal. */
    fun replace(state: Map<String,Map<String,Any>>) {
        val allNames=(listNames()+state.keys).distinct()
        for(name in allNames) {
            require(safeName.matches(name))
            val editor=context.getSharedPreferences(name,Context.MODE_PRIVATE)
                .edit().clear()
            state[name].orEmpty().forEach { (key,value) ->
                when(value) {
                    is String -> editor.putString(key,value)
                    is Boolean -> editor.putBoolean(key,value)
                    is Int -> editor.putInt(key,value)
                    is Long -> editor.putLong(key,value)
                    is Float -> editor.putFloat(key,value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        editor.putStringSet(key,(value as Set<String>).toSet())
                    }
                    else -> error("Unknown preference value")
                }
            }
            check(editor.commit()) { "Could not restore preferences: " + name }
        }
    }
}
