package br.com.itau.etl.aggregator.model

import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.LocalDateTime

data class Transaction(
    val id: String,
    val accountId: String,
    val amount: BigDecimal,
    val type: TransactionType,
    val description: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val timestamp: LocalDateTime,
    val pixData: PixData? = null
)

enum class TransactionType {
    PIX, TED, DOC, CREDIT, DEBIT
}

data class PixData(
    val pixKey: String,
    val pixKeyType: PixKeyType,
    val endToEndId: String
)

enum class PixKeyType {
    CPF, CNPJ, EMAIL, PHONE, RANDOM
}

data class TransactionSummary(
    val period: String,
    val totalTransactions: Long,
    val totalAmount: BigDecimal,
    val transactionsByType: Map<TransactionType, TransactionTypeStats>,
    val topAccounts: List<AccountStats>,
    val pixStats: PixStats,
    val generatedAt: LocalDateTime
)

data class TransactionTypeStats(
    val count: Long,
    val totalAmount: BigDecimal,
    val averageAmount: BigDecimal
)

data class AccountStats(
    val accountId: String,
    val transactionCount: Long,
    val totalAmount: BigDecimal
)

data class PixStats(
    val totalPixTransactions: Long,
    val totalPixAmount: BigDecimal,
    val pixByKeyType: Map<PixKeyType, Long>
)
