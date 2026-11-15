package com.myanmar.ledger2d.core.domain

import com.myanmar.ledger2d.core.model.DrawIdentity
import com.myanmar.ledger2d.core.model.DrawSession
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Base64

/**
 * Versioned, checksum-verified, size-bounded portable checkpoint format.
 *
 * This codec does NOT store anything by itself. Android integration must
 * write each complete encoded snapshot atomically, only from a user-started
 * search; restored checkpoints must pass target/kind/history verification.
 */
internal object CherryV2CheckpointCodec {
    private const val MAGIC = 0x43325632 // C2V2
    private const val FORMAT_VERSION = 1
    private const val PREFIX = "CHERRY-V2:"
    private const val MAX_BYTES = 256_000
    private const val MAX_EXPRESSION_CHARS = 8_000

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    fun encode(state: CherryV2StreakRegistry): String {
        require(state.firstByStreak.size <=
            CherryFormulaDiscoveryEngine.SEARCH_DRAWS)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(FORMAT_VERSION)
            output.writeUTF(state.target.date.toString())
            output.writeUTF(state.target.session.name)
            output.writeUTF(state.kind.name)
            output.writeUTF(state.historySignature)
            output.writeUTF(state.nextIndex.toString())
            output.writeInt(state.grammarVersion)
            output.writeInt(state.firstByStreak.size)
            for ((streak, formula) in state.firstByStreak.toSortedMap()) {
                require(formula.expression.length <= MAX_EXPRESSION_CHARS)
                output.writeInt(streak)
                output.writeUTF(formula.formulaIndex.toString())
                output.writeUTF(formula.expression)
                output.writeInt(formula.predictedDigit)
                output.writeInt(formula.offset)
                output.writeInt(formula.grammarVersion)
            }
            output.writeInt(state.recentBehaviorKeys.size)
            for (key in state.recentBehaviorKeys) {
                require(key.isNotEmpty() && key.length <=
                    CherryFormulaDiscoveryEngine.SEARCH_DRAWS + 16)
                output.writeUTF(key)
            }
        }
        val bytes = buffer.toByteArray()
        require(bytes.size <= MAX_BYTES) { "Checkpoint size exceeds budget" }
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return PREFIX + payload + ":" + digest(bytes)
    }

    /**
     * Returns null for malformed, stale or tampered data, so app integration
     * can safely start a fresh search rather than reuse incompatible results.
     */
    fun decode(
        serialized: String,
        expectedTarget: DrawIdentity,
        expectedKind: CherryDiscoveryKind,
        expectedHistorySignature: String,
    ): CherryV2StreakRegistry? = runCatching {
        require(serialized.length <= MAX_BYTES * 2)
        require(serialized.startsWith(PREFIX))
        val pair = serialized.substring(PREFIX.length).split(':')
        require(pair.size == 2)
        val bytes = Base64.getUrlDecoder().decode(pair[0])
        require(bytes.size <= MAX_BYTES && digest(bytes) == pair[1])
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            require(input.readInt() == MAGIC)
            require(input.readInt() == FORMAT_VERSION)
            val target = DrawIdentity(
                LocalDate.parse(input.readUTF()),
                DrawSession.valueOf(input.readUTF()))
            val kind = CherryDiscoveryKind.valueOf(input.readUTF())
            val signature = input.readUTF()
            require(target == expectedTarget && kind == expectedKind &&
                signature == expectedHistorySignature)
            val index = BigInteger(input.readUTF())
            val grammarVersion = input.readInt()
            require(grammarVersion == CherryV2ExpandableGrammar.VERSION)
            val count = input.readInt()
            require(count in 0..CherryFormulaDiscoveryEngine.SEARCH_DRAWS)
            val formulas = linkedMapOf<Int, CherryV2StreakMatch>()
            repeat(count) {
                val streak = input.readInt()
                val formulaIndex = BigInteger(input.readUTF())
                val expression = input.readUTF()
                require(expression.length <= MAX_EXPRESSION_CHARS)
                val prediction = input.readInt()
                val offset = input.readInt()
                val formulaGrammar = input.readInt()
                require(streak !in formulas)
                formulas[streak] = CherryV2StreakMatch(
                    target, kind, signature, formulaIndex, expression,
                    prediction, streak, offset, formulaGrammar)
            }
            val behaviorCount = input.readInt()
            require(behaviorCount in 0..CherryV2StreakRegistry.BEHAVIOR_WINDOW)
            val behaviors = ArrayList<String>(behaviorCount)
            repeat(behaviorCount) {
                val key = input.readUTF()
                require(key.isNotEmpty() && key.length <=
                    CherryFormulaDiscoveryEngine.SEARCH_DRAWS + 16)
                behaviors.add(key)
            }
            require(input.available() == 0)
            CherryV2StreakRegistry(
                target, kind, signature, index, formulas, behaviors,
                grammarVersion)
        }
    }.getOrNull()
}
