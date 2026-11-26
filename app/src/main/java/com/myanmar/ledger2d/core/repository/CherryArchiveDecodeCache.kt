package com.myanmar.ledger2d.core.repository

/** Reuse only byte-for-byte unchanged persisted values; changed/deleted values
 * are always reflected. A failed decode never replaces the previous cache. */
internal class CherryArchiveDecodeCache<T> {
    private var entries=emptyMap<String,Pair<String,T>>()
    fun read(raw:Map<String,*>,decode:(String,String)->T):List<T> {
        val next=raw.mapValues{(key,value)->
            val encoded=value as String
            entries[key]?.takeIf{it.first==encoded} ?: (encoded to decode(key,encoded))
        }
        entries=next
        return next.values.map{it.second}
    }
}
