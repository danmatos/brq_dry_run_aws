package br.com.itau.etl.aggregator.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionModelTest : BehaviorSpec({

    val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    Given("um modelo de transação") {
        When("serializando e deserializando uma transação PIX") {
            val pixTransaction = Transaction(
                id = "txn-pix-001",
                accountId = "acc-001",
                amount = BigDecimal("150.50"),
                type = TransactionType.PIX,
                description = "PIX payment test",
                timestamp = LocalDateTime.of(2024, 1, 15, 14, 30, 45),
                pixData = PixData(
                    pixKey = "user@example.com",
                    pixKeyType = PixKeyType.EMAIL,
                    endToEndId = "E123456789202401151430"
                )
            )

            val json = objectMapper.writeValueAsString(pixTransaction)
            val deserializedTransaction: Transaction = objectMapper.readValue(json)

            Then("deve manter todos os dados íntegros") {
                deserializedTransaction.id shouldBe pixTransaction.id
                deserializedTransaction.accountId shouldBe pixTransaction.accountId
                deserializedTransaction.amount shouldBe pixTransaction.amount
                deserializedTransaction.type shouldBe pixTransaction.type
                deserializedTransaction.description shouldBe pixTransaction.description
                deserializedTransaction.timestamp shouldBe pixTransaction.timestamp
                
                deserializedTransaction.pixData shouldNotBe null
                deserializedTransaction.pixData!!.pixKey shouldBe pixTransaction.pixData!!.pixKey
                deserializedTransaction.pixData!!.pixKeyType shouldBe pixTransaction.pixData!!.pixKeyType
                deserializedTransaction.pixData!!.endToEndId shouldBe pixTransaction.pixData!!.endToEndId
            }
        }

        When("serializando uma transação sem dados PIX") {
            val tedTransaction = Transaction(
                id = "txn-ted-001",
                accountId = "acc-002",
                amount = BigDecimal("1000.00"),
                type = TransactionType.TED,
                description = "TED transfer",
                timestamp = LocalDateTime.of(2024, 1, 15, 16, 0, 0),
                pixData = null
            )

            val json = objectMapper.writeValueAsString(tedTransaction)
            val deserializedTransaction: Transaction = objectMapper.readValue(json)

            Then("deve serializar corretamente sem dados PIX") {
                deserializedTransaction.pixData shouldBe null
                deserializedTransaction.type shouldBe TransactionType.TED
            }
        }

        When("testando diferentes tipos de chave PIX") {
            val cpfPixData = PixData("12345678901", PixKeyType.CPF, "E001")
            val emailPixData = PixData("user@domain.com", PixKeyType.EMAIL, "E002")
            val phonePixData = PixData("+5511999999999", PixKeyType.PHONE, "E003")
            val cnpjPixData = PixData("12345678000199", PixKeyType.CNPJ, "E004")
            val randomPixData = PixData("a1b2c3d4-e5f6-7890", PixKeyType.RANDOM, "E005")

            Then("deve aceitar todos os tipos de chave PIX válidos") {
                cpfPixData.pixKeyType shouldBe PixKeyType.CPF
                emailPixData.pixKeyType shouldBe PixKeyType.EMAIL
                phonePixData.pixKeyType shouldBe PixKeyType.PHONE
                cnpjPixData.pixKeyType shouldBe PixKeyType.CNPJ
                randomPixData.pixKeyType shouldBe PixKeyType.RANDOM
            }
        }
    }

    Given("um modelo de resumo de transações") {
        When("criando um resumo completo") {
            val summary = TransactionSummary(
                period = "2024-01-15-14",
                totalTransactions = 100,
                totalAmount = BigDecimal("50000.00"),
                transactionsByType = mapOf(
                    TransactionType.PIX to TransactionTypeStats(60, BigDecimal("30000.00"), BigDecimal("500.00")),
                    TransactionType.TED to TransactionTypeStats(25, BigDecimal("15000.00"), BigDecimal("600.00")),
                    TransactionType.DOC to TransactionTypeStats(15, BigDecimal("5000.00"), BigDecimal("333.33"))
                ),
                topAccounts = listOf(
                    AccountStats("acc-001", 25, BigDecimal("12500.00")),
                    AccountStats("acc-002", 20, BigDecimal("10000.00")),
                    AccountStats("acc-003", 15, BigDecimal("7500.00"))
                ),
                pixStats = PixStats(
                    totalPixTransactions = 60,
                    totalPixAmount = BigDecimal("30000.00"),
                    pixByKeyType = mapOf(
                        PixKeyType.EMAIL to 30L,
                        PixKeyType.CPF to 20L,
                        PixKeyType.PHONE to 10L
                    )
                ),
                generatedAt = LocalDateTime.of(2024, 1, 15, 14, 45, 30)
            )

            val json = objectMapper.writeValueAsString(summary)
            val deserializedSummary: TransactionSummary = objectMapper.readValue(json)

            Then("deve serializar e deserializar corretamente") {
                deserializedSummary.period shouldBe summary.period
                deserializedSummary.totalTransactions shouldBe summary.totalTransactions
                deserializedSummary.totalAmount shouldBe summary.totalAmount
                deserializedSummary.transactionsByType.size shouldBe 3
                deserializedSummary.topAccounts.size shouldBe 3
                deserializedSummary.pixStats.totalPixTransactions shouldBe 60
                deserializedSummary.generatedAt shouldBe summary.generatedAt
            }
        }

        When("verificando cálculos nas estatísticas") {
            val stats = TransactionTypeStats(
                count = 10,
                totalAmount = BigDecimal("5000.00"),
                averageAmount = BigDecimal("500.00")
            )

            Then("deve manter precisão decimal") {
                stats.count shouldBe 10
                stats.totalAmount shouldBe BigDecimal("5000.00")
                stats.averageAmount shouldBe BigDecimal("500.00")
            }
        }
    }

    Given("diferentes tipos de transação") {
        When("verificando enums de tipo de transação") {
            Then("deve ter todos os tipos esperados") {
                TransactionType.values().size shouldBe 5
                TransactionType.values() shouldBe arrayOf(
                    TransactionType.PIX,
                    TransactionType.TED,
                    TransactionType.DOC,
                    TransactionType.CREDIT,
                    TransactionType.DEBIT
                )
            }
        }

        When("verificando enums de tipo de chave PIX") {
            Then("deve ter todos os tipos de chave esperados") {
                PixKeyType.values().size shouldBe 5
                PixKeyType.values() shouldBe arrayOf(
                    PixKeyType.CPF,
                    PixKeyType.CNPJ,
                    PixKeyType.EMAIL,
                    PixKeyType.PHONE,
                    PixKeyType.RANDOM
                )
            }
        }
    }
})
