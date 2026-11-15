package com.myanmar.ledger2d.core.repository

import android.content.Context
import com.myanmar.ledger2d.core.domain.CherryDiscoveryKind
import com.myanmar.ledger2d.core.domain.CherryV2CheckpointCodec
import com.myanmar.ledger2d.core.domain.CherryV2GuidedSchedule
import com.myanmar.ledger2d.core.domain.CherryV2StreakRegistry
import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.time.LocalDate

/**
 * Isolated V2-only local checkpoint store. It never starts discovery, saves
 * lottery results, or changes DOC/Gemini/Champion. The explicit search caller
 * must invoke persist() off Main and supply a history-validated context for
 * resume(). Unlike a transient scan cursor, this survives process restart.
 */
internal class CherryV2CheckpointStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "cherry_v2_discovery_cursors_v2", Context.MODE_PRIVATE)

    companion object {
        private val processLock = Any()

        private fun stateKey(kind: CherryDiscoveryKind) = "cursor_" + kind.name
        private fun metadataKey(kind: CherryDiscoveryKind) = "meta_" + kind.name
    }

    private data class Metadata(
        val target: DrawIdentity,
        val kind: CherryDiscoveryKind,
        val signature: String,
        val scheduleVersion: Int,
    ) {
        fun serialize(): String = listOf(
            scheduleVersion.toString(), target.date.toString(),
            target.session.name, kind.name, signature,
        ).joinToString("|")

        companion object {
            fun parse(text: String): Metadata {
                val parts = text.split("|")
                require(parts.size == 5 && parts.all { it.isNotBlank() })
                return Metadata(
                    DrawIdentity(
                        LocalDate.parse(parts[1]),
                        DrawSession.valueOf(parts[2]),
                    ),
                    CherryDiscoveryKind.valueOf(parts[3]),
                    parts[4],
                    parts[0].toInt(),
                )
            }
        }
    }

    /**
     * Corruption fails closed instead of silently resetting a same-version
     * search. A known older schedule version is intentionally incompatible
     * and can be replaced by a fresh search; its IDs mean different formulas.
     */
    private fun read(kind: CherryDiscoveryKind): CherryV2StreakRegistry? {
        val raw = preferences.getString(stateKey(kind), null)
        val header = preferences.getString(metadataKey(kind), null)
        if (raw == null && header == null) return null
        if (raw == null || header == null) {
            throw IllegalStateException("Incomplete Cherry V2 checkpoint")
        }
        val meta = try {
            Metadata.parse(header).also { require(it.kind == kind) }
        } catch (error: Exception) {
            throw IllegalStateException("Corrupt Cherry V2 checkpoint metadata", error)
        }
        if (meta.scheduleVersion != CherryV2GuidedSchedule.VERSION) return null
        return CherryV2CheckpointCodec.decode(
            raw, meta.target, meta.kind, meta.signature,
        ) ?: throw IllegalStateException("Corrupt Cherry V2 checkpoint payload")
    }

    /** Saves one complete immutable state; rejects stale writers/erasure. */
    fun persist(state: CherryV2StreakRegistry) = synchronized(processLock) {
        val previous = read(state.kind)
        if (previous != null && previous.target == state.target &&
            previous.historySignature == state.historySignature) {
            require(state.nextIndex >= previous.nextIndex) {
                "Cherry V2 cursor cannot move backwards"
            }
            require(previous.firstByStreak.all { (streak, formula) ->
                state.firstByStreak[streak] == formula
            }) { "Cherry V2 cannot delete or replace previously found formulas" }
            if (state.nextIndex == previous.nextIndex) {
                require(previous == state) {
                    "Same-position checkpoint cannot be silently rewritten"
                }
            }
        }
        val snapshot = CherryV2CheckpointCodec.encode(state)
        val header = Metadata(
            state.target, state.kind, state.historySignature,
            state.grammarVersion,
        ).serialize()
        check(preferences.edit()
            .putString(stateKey(state.kind), snapshot)
            .putString(metadataKey(state.kind), header)
            .commit()) { "Cherry V2 checkpoint commit failed" }
    }

    /**
     * A new target, corrected result/history or changed schedule returns
     * null; never use a stale candidate against a different draw.
     */
    fun resume(
        kind: CherryDiscoveryKind,
        target: DrawIdentity,
        historySignature: String,
    ): CherryV2StreakRegistry? = synchronized(processLock) {
        val saved = read(kind) ?: return@synchronized null
        if (saved.target != target ||
            saved.historySignature != historySignature) null else saved
    }
}
