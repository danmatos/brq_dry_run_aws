package br.com.itau.etl.aggregator.service

import br.com.itau.etl.aggregator.model.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionAggregatorServiceTest : BehaviorSpec({

    lateinit var aggregatorService: TransactionAggregatorService

    beforeEach {
        aggregatorService = TransactionAggregatorService()
    }

    Given("um serviço de agregação de transações") {
        When("uma transação é adicionada") {
            val transaction = createSampleTransaction()
            aggregatorService.addTransaction(transaction)

            Then("deve ser armazenada no buffer do período atual") {
                val bufferStatus = aggregatorService.getBufferStatus()
                val currentPeriod = aggregatorService.getCurrentPeriod()
                
                bufferStatus shouldContainKey currentPeriod
                bufferStatus[currentPeriod] shouldBe 1
            }
        }

        When("múltiplas transações são adicionadas no mesmo período") {
            val transactions = createMultipleTransactions(5)
            transactions.forEach { aggregatorService.addTransaction(it) }

            Then("todas devem ser armazenadas no buffer") {
                val bufferStatus = aggregatorService.getBufferStatus()
                val currentPeriod = aggregatorService.getCurrentPeriod()
                
                bufferStatus[currentPeriod] shouldBe 5
            }

            And("deve gerar um resumo completo") {
                val summary = aggregatorService.generateSummary()
                
                summary shouldNotBe null
                summary!!.totalTransactions shouldBe 5
                summary.period shouldBe aggregatorService.getCurrentPeriod()
                summary.transactionsByType shouldContainKey TransactionType.PIX
                summary.transactionsByType shouldContainKey TransactionType.TED
            }
        }

        When("gerando resumo sem transações") {
            Then("deve retornar null") {
                val summary = aggregatorService.generateSummary()
                summary shouldBe null
            }
        }

        When("calculando estatísticas por tipo de transação") {
            val pixTransactions = listOf(
                createPixTransaction("1", BigDecimal("100.00")),
                createPixTransaction("2", BigDecimal("200.00"))
            )
            val tedTransaction = createTedTransaction("3", BigDecimal("500.00"))

            pixTransactions.forEach { aggregatorService.addTransaction(it) }
            aggregatorService.addTransaction(tedTransaction)

            Then("deve calcular corretamente as estatísticas") {
                val summary = aggregatorService.generateSummary()!!
                
                // PIX statistics
                val pixStats = summary.transactionsByType[TransactionType.PIX]!!
                pixStats.count shouldBe 2
                pixStats.totalAmount shouldBe BigDecimal("300.00")
                pixStats.averageAmount shouldBe BigDecimal("150.00")

                // TED statistics
                val tedStats = summary.transactionsByType[TransactionType.TED]!!
                tedStats.count shouldBe 1
                tedStats.totalAmount shouldBe BigDecimal("500.00")
                tedStats.averageAmount shouldBe BigDecimal("500.00")
            }
        }

        When("calculando top accounts") {
            val transactions = listOf(
                createTransactionForAccount("account1", BigDecimal("1000.00")),
                createTransactionForAccount("account1", BigDecimal("500.00")),
                createTransactionForAccount("account2", BigDecimal("2000.00")),
                createTransactionForAccount("account3", BigDecimal("100.00"))
            )
            
            transactions.forEach { aggregatorService.addTransaction(it) }

            Then("deve ordenar accounts por volume total") {
                val summary = aggregatorService.generateSummary()!!
                
                summary.topAccounts shouldHaveSize 3
                summary.topAccounts[0].accountId shouldBe "account2"
                summary.topAccounts[0].totalAmount shouldBe BigDecimal("2000.00")
                summary.topAccounts[1].accountId shouldBe "account1"
                summary.topAccounts[1].totalAmount shouldBe BigDecimal("1500.00")
                summary.topAccounts[2].accountId shouldBe "account3"
                summary.topAccounts[2].totalAmount shouldBe BigDecimal("100.00")
            }
        }

        When("calculando estatísticas PIX") {
            val pixTransactions = listOf(
                createPixTransactionWithKeyType("1", PixKeyType.CPF, BigDecimal("100.00")),
                createPixTransactionWithKeyType("2", PixKeyType.EMAIL, BigDecimal("200.00")),
                createPixTransactionWithKeyType("3", PixKeyType.CPF, BigDecimal("300.00"))
            )
            
            pixTransactions.forEach { aggregatorService.addTransaction(it) }

            Then("deve calcular estatísticas PIX corretamente") {
                val summary = aggregatorService.generateSummary()!!
                
                summary.pixStats.totalPixTransactions shouldBe 3
                summary.pixStats.totalPixAmount shouldBe BigDecimal("600.00")
                summary.pixStats.pixByKeyType[PixKeyType.CPF] shouldBe 2L
                summary.pixStats.pixByKeyType[PixKeyType.EMAIL] shouldBe 1L
            }
        }

        When("limpando um período específico") {
            val transaction = createSampleTransaction()
            aggregatorService.addTransaction(transaction)
            val currentPeriod = aggregatorService.getCurrentPeriod()

            aggregatorService.clearPeriod(currentPeriod)

            Then("o buffer deve estar vazio para esse período") {
                val bufferStatus = aggregatorService.getBufferStatus()
                bufferStatus[currentPeriod] shouldBe null
            }
        }
    }
})

// Helper functions
private fun createSampleTransaction(): Transaction {
    return Transaction(
        id = "txn-001",
        accountId = "acc-001",
        amount = BigDecimal("150.00"),
        type = TransactionType.PIX,
        description = "Sample transaction",
        timestamp = LocalDateTime.now(),
        pixData = PixData(
            pixKey = "user@example.com",
            pixKeyType = PixKeyType.EMAIL,
            endToEndId = "E123456789"
        )
    )
}

private fun createMultipleTransactions(count: Int): List<Transaction> {
    return (1..count).map { i ->
        Transaction(
            id = "txn-${i.toString().padStart(3, '0')}",
            accountId = "acc-${i % 3 + 1}",
            amount = BigDecimal(i * 100),
            type = if (i % 2 == 0) TransactionType.PIX else TransactionType.TED,
            description = "Transaction $i",
            timestamp = LocalDateTime.now(),
            pixData = if (i % 2 == 0) PixData(
                pixKey = "user$i@example.com",
                pixKeyType = PixKeyType.EMAIL,
                endToEndId = "E$i"
            ) else null
        )
    }
}

private fun createPixTransaction(id: String, amount: BigDecimal): Transaction {
    return Transaction(
        id = id,
        accountId = "acc-001",
        amount = amount,
        type = TransactionType.PIX,
        description = "PIX transaction",
        timestamp = LocalDateTime.now(),
        pixData = PixData(
            pixKey = "user@example.com",
            pixKeyType = PixKeyType.EMAIL,
            endToEndId = "E$id"
        )
    )
}

private fun createTedTransaction(id: String, amount: BigDecimal): Transaction {
    return Transaction(
        id = id,
        accountId = "acc-002",
        amount = amount,
        type = TransactionType.TED,
        description = "TED transaction",
        timestamp = LocalDateTime.now()
    )
}

private fun createTransactionForAccount(accountId: String, amount: BigDecimal): Transaction {
    return Transaction(
        id = "txn-${System.currentTimeMillis()}",
        accountId = accountId,
        amount = amount,
        type = TransactionType.PIX,
        description = "Transaction for $accountId",
        timestamp = LocalDateTime.now()
    )
}

private fun createPixTransactionWithKeyType(id: String, keyType: PixKeyType, amount: BigDecimal): Transaction {
    val pixKey = when (keyType) {
        PixKeyType.CPF -> "12345678901"
        PixKeyType.EMAIL -> "user$id@example.com"
        PixKeyType.PHONE -> "+5511999999999"
        PixKeyType.CNPJ -> "12345678000199"
        PixKeyType.RANDOM -> "random-key-$id"
    }
    
    return Transaction(
        id = id,
        accountId = "acc-001",
        amount = amount,
        type = TransactionType.PIX,
        description = "PIX with $keyType",
        timestamp = LocalDateTime.now(),
        pixData = PixData(
            pixKey = pixKey,
            pixKeyType = keyType,
            endToEndId = "E$id"
        )
    )
}
