package com.myanmar.ledger2d.core.model

import java.time.LocalDate

enum class DrawSession(val label: String) { MORNING("မနက်"), EVENING("ညနေ") }

data class Agent(val id: Long = 0, val name: String, val address: String = "", val phone: String = "", val rate: Long, val remark: String = "", val createdAt: Long, val updatedAt: Long)
data class Customer(val id: Long = 0, val agentId: Long, val name: String, val address: String = "", val phone: String = "", val remark: String = "", val commissionRateBasisPoints: Int = 0, val createdAt: Long, val updatedAt: Long)
data class ExpandedBet(val digit: String, val amount: Long)
data class DrawIdentity(val date: LocalDate, val session: DrawSession)
data class DigitTotal(val digit: String, val amount: Long)
data class EffectiveLimits(val allLimit: Long?, val specialLimits: Map<String, Long>) { fun forDigit(digit: String): Long? = specialLimits[digit] ?: allLimit }
