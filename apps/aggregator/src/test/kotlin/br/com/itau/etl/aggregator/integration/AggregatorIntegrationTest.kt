package br.com.itau.etl.aggregator.integration

import br.com.itau.etl.aggregator.model.*
import br.com.itau.etl.aggregator.service.TransactionAggregatorService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldContainKey
import org.awaitility.kotlin.await
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = ["test-transactions"],
    brokerProperties = [
        "listeners=PLAINTEXT://localhost:9092",
        "port=9092"
    ]
)
@TestPropertySource(
    properties = [
        "app.kafka.topics.transactions=test-transactions",
        "spring.kafka.bootstrap-servers=localhost:9092",
        "spring.kafka.consumer.group-id=test-aggregator-group",
        "spring.kafka.consumer.auto-offset-reset=earliest"
    ]
)
@DirtiesContext
class AggregatorIntegrationTest : BehaviorSpec({

    Given("um sistema completo de agregação") {
        When("processando múltiplas transações em sequência") {
            val aggregatorService = TransactionAggregatorService()
            val transactions = createTestTransactions()

            // Simular processamento sequencial como seria no Kafka
            transactions.forEach { transaction ->
                aggregatorService.addTransaction(transaction)
            }

            Then("deve agregar corretamente todas as transações") {
                val summary = aggregatorService.generateSummary()
                
                summary shouldNotBe null
                summary!!.totalTransactions shouldBe transactions.size.toLong()
                summary.transactionsByType shouldContainKey TransactionType.PIX
                summary.transactionsByType shouldContainKey TransactionType.TED
                summary.transactionsByType shouldContainKey TransactionType.CREDIT
                
                // Verificar totais
                val expectedTotal = transactions.sumOf { it.amount }
                summary.totalAmount shouldBe expectedTotal
            }
        }

        When("processando diferentes períodos") {
            val aggregatorService = TransactionAggregatorService()
            val currentPeriod = aggregatorService.getCurrentPeriod()
            
            // Adicionar transações ao período atual
            val currentTransactions = createTestTransactions().take(5)
            currentTransactions.forEach { aggregatorService.addTransaction(it) }
            
            val currentSummary = aggregatorService.generateSummary(currentPeriod)

            Then("deve manter separação por período") {
                currentSummary shouldNotBe null
                currentSummary!!.period shouldBe currentPeriod
                currentSummary.totalTransactions shouldBe 5L
                
                // Verificar que outros períodos estão vazios
                val otherPeriodSummary = aggregatorService.generateSummary("2023-12-01-10")
                otherPeriodSummary shouldBe null
            }
        }

        When("testando performance com alta volumetria") {
            val aggregatorService = TransactionAggregatorService()
            val startTime = System.currentTimeMillis()
            
            // Gerar 1000 transações
            val largeTransactionSet = (1..1000).map { i ->
                createTransactionWithId("txn-$i", BigDecimal(i * 10))
            }
            
            largeTransactionSet.forEach { aggregatorService.addTransaction(it) }
            val summary = aggregatorService.generateSummary()
            
            val endTime = System.currentTimeMillis()
            val processingTime = endTime - startTime

            Then("deve processar rapidamente e manter acurácia") {
                summary shouldNotBe null
                summary!!.totalTransactions shouldBe 1000L
                
                // Performance: deve processar 1000 transações em menos de 5 segundos
                processingTime shouldBe <=(5000L)
                
                // Verificar que top accounts estão corretos
                summary.topAccounts shouldHaveSize 10  // Limited to top 10
                summary.topAccounts[0].totalAmount shouldBe >(summary.topAccounts[1].totalAmount)
            }
        }

        When("testando cenário completo de PIX") {
            val aggregatorService = TransactionAggregatorService()
            val pixTransactions = createVariedPixTransactions()
            
            pixTransactions.forEach { aggregatorService.addTransaction(it) }
            val summary = aggregatorService.generateSummary()

            Then("deve calcular estatísticas PIX detalhadas") {
                summary shouldNotBe null
                summary!!.pixStats.totalPixTransactions shouldBe pixTransactions.size.toLong()
                
                // Verificar distribuição por tipo de chave
                val expectedCpfCount = pixTransactions.count { 
                    it.pixData?.pixKeyType == PixKeyType.CPF 
                }.toLong()
                
                val expectedEmailCount = pixTransactions.count { 
                    it.pixData?.pixKeyType == PixKeyType.EMAIL 
                }.toLong()
                
                summary.pixStats.pixByKeyType[PixKeyType.CPF] shouldBe expectedCpfCount
                summary.pixStats.pixByKeyType[PixKeyType.EMAIL] shouldBe expectedEmailCount
            }
        }

        When("testando limpeza de períodos") {
            val aggregatorService = TransactionAggregatorService()
            val testTransaction = createSampleTransaction()
            
            aggregatorService.addTransaction(testTransaction)
            val currentPeriod = aggregatorService.getCurrentPeriod()
            
            // Verificar que existe
            var summary = aggregatorService.generateSummary(currentPeriod)
            summary shouldNotBe null
            
            // Limpar período
            aggregatorService.clearPeriod(currentPeriod)
            
            // Verificar que foi limpo
            summary = aggregatorService.generateSummary(currentPeriod)
            summary shouldBe null

            Then("o buffer deve estar limpo") {
                val bufferStatus = aggregatorService.getBufferStatus()
                bufferStatus[currentPeriod] shouldBe null
            }
        }
    }
})

// Funções auxiliares para testes de integração
private fun createTestTransactions(): List<Transaction> {
    return listOf(
        createPixTransaction("pix-1", "acc-001", BigDecimal("100.00")),
        createPixTransaction("pix-2", "acc-001", BigDecimal("250.00")),
        createTedTransaction("ted-1", "acc-002", BigDecimal("500.00")),
        createTedTransaction("ted-2", "acc-003", BigDecimal("750.00")),
        createCreditTransaction("cred-1", "acc-001", BigDecimal("1000.00")),
        createDebitTransaction("deb-1", "acc-002", BigDecimal("50.00")),
        createDocTransaction("doc-1", "acc-003", BigDecimal("300.00"))
    )
}

private fun createVariedPixTransactions(): List<Transaction> {
    return listOf(
        createPixTransactionWithKeyType("pix-cpf-1", PixKeyType.CPF, BigDecimal("100.00")),
        createPixTransactionWithKeyType("pix-cpf-2", PixKeyType.CPF, BigDecimal("200.00")),
        createPixTransactionWithKeyType("pix-email-1", PixKeyType.EMAIL, BigDecimal("150.00")),
        createPixTransactionWithKeyType("pix-email-2", PixKeyType.EMAIL, BigDecimal("300.00")),
        createPixTransactionWithKeyType("pix-phone-1", PixKeyType.PHONE, BigDecimal("75.00")),
        createPixTransactionWithKeyType("pix-random-1", PixKeyType.RANDOM, BigDecimal("400.00"))
    )
}

private fun createSampleTransaction(): Transaction {
    return createPixTransaction("sample-1", "acc-sample", BigDecimal("123.45"))
}

private fun createTransactionWithId(id: String, amount: BigDecimal): Transaction {
    return Transaction(
        id = id,
        accountId = "acc-${id.hashCode() % 10}",
        amount = amount,
        type = TransactionType.values()[id.hashCode() % TransactionType.values().size],
        description = "Integration test transaction",
        timestamp = LocalDateTime.now(),
        pixData = null
    )
}

private fun createPixTransaction(id: String, accountId: String, amount: BigDecimal): Transaction {
    return Transaction(
        id = id,
        accountId = accountId,
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

private fun createTedTransaction(id: String, accountId: String, amount: BigDecimal): Transaction {
    return Transaction(
        id = id,
        accountId = accountId,
        amount = amount,
        type = TransactionType.TED,
        description = "TED transaction",
        timestamp = LocalDateTime.now()
    )
}

private fun createCreditTransaction(id: String, accountId: String, amount: BigDecimal): Transaction {
    return Transaction(
        id = id,
        accountId = accountId,
        amount = amount,
        type = TransactionType.CREDIT,
        description = "Credit transaction",
        timestamp = LocalDateTime.now()
    )
}

private fun createDebitTransaction(id: String, accountId: String, amount: BigDecimal): Transaction {
    return Transaction(
        id = id,
        accountId = accountId,
        amount = amount,
        type = TransactionType.DEBIT,
        description = "Debit transaction",
        timestamp = LocalDateTime.now()
    )
}

private fun createDocTransaction(id: String, accountId: String, amount: BigDecimal): Transaction {
    return Transaction(
        id = id,
        accountId = accountId,
        amount = amount,
        type = TransactionType.DOC,
        description = "DOC transaction",
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
