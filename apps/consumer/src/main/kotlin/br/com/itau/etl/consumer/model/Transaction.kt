package br.com.itau.etl.consumer.model

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

data class ProcessedTransaction(
    val transactionId: String,
    val accountId: String,
    val amount: BigDecimal,
    val type: TransactionType,
    val description: String,
    val timestamp: LocalDateTime,
    val processedAt: LocalDateTime,
    val pixData: PixData? = null,
    val status: TransactionStatus = TransactionStatus.PROCESSED
)

enum class TransactionStatus {
    PROCESSED, REJECTED, ERROR
}
