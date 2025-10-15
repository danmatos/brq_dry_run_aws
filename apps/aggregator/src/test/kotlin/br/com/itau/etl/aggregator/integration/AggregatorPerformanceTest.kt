package br.com.itau.etl.aggregator.integration

import br.com.itau.etl.aggregator.model.*
import br.com.itau.etl.aggregator.service.TransactionAggregatorService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class AggregatorPerformanceTest : BehaviorSpec({

    Given("um teste de performance para agregação") {
        When("processando 10.000 transações sequencialmente") {
            val aggregatorService = TransactionAggregatorService()
            val transactionCount = 10_000
            val startTime = System.currentTimeMillis()

            repeat(transactionCount) { i ->
                val transaction = generateRandomTransaction(i)
                aggregatorService.addTransaction(transaction)
            }

            val summary = aggregatorService.generateSummary()
            val endTime = System.currentTimeMillis()
            val processingTime = endTime - startTime

            Then("deve processar rapidamente mantendo acurácia") {
                summary shouldNotBe null
                summary!!.totalTransactions shouldBe transactionCount.toLong()
                
                // Performance: deve processar 10k transações em menos de 2 segundos
                processingTime shouldBeLessThan 2000L
                
                // Verificar integridade dos dados
                summary.transactionsByType.values.sumOf { it.count } shouldBe transactionCount.toLong()
                summary.topAccounts.size shouldBeGreaterThan 0
                
                println("Processed $transactionCount transactions in ${processingTime}ms")
                println("Throughput: ${transactionCount * 1000 / processingTime} transactions/second")
            }
        }

        When("processando transações concorrentemente") {
            val aggregatorService = TransactionAggregatorService()
            val transactionCount = 5_000
            val concurrencyLevel = 10
            val transactionsPerThread = transactionCount / concurrencyLevel
            val startTime = System.currentTimeMillis()

            coroutineScope {
                val jobs = (1..concurrencyLevel).map { threadId ->
                    async {
                        repeat(transactionsPerThread) { i ->
                            val transaction = generateRandomTransaction(threadId * 1000 + i)
                            aggregatorService.addTransaction(transaction)
                        }
                    }
                }
                jobs.awaitAll()
            }

            val summary = aggregatorService.generateSummary()
            val endTime = System.currentTimeMillis()
            val processingTime = endTime - startTime

            Then("deve manter thread safety e consistência") {
                summary shouldNotBe null
                summary!!.totalTransactions shouldBe transactionCount.toLong()
                
                // Verificar que não houve perda de dados por concorrência
                val totalFromTypes = summary.transactionsByType.values.sumOf { it.count }
                totalFromTypes shouldBe transactionCount.toLong()
                
                println("Concurrent processing: $transactionCount transactions in ${processingTime}ms")
                println("Concurrent throughput: ${transactionCount * 1000 / processingTime} transactions/second")
            }
        }

        When("testando uso de memória com grandes volumes") {
            val aggregatorService = TransactionAggregatorService()
            val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            
            // Processar 50k transações em batches
            val batchSize = 1_000
            val totalBatches = 50
            
            repeat(totalBatches) { batch ->
                repeat(batchSize) { i ->
                    val transaction = generateRandomTransaction(batch * batchSize + i)
                    aggregatorService.addTransaction(transaction)
                }
                
                // Force garbage collection periodicamente
                if (batch % 10 == 0) {
                    System.gc()
                    Thread.sleep(10)
                }
            }
            
            val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
            val memoryUsed = (finalMemory - initialMemory) / 1024 / 1024 // MB
            
            val summary = aggregatorService.generateSummary()

            Then("deve usar memória de forma eficiente") {
                summary shouldNotBe null
                summary!!.totalTransactions shouldBe (totalBatches * batchSize).toLong()
                
                // Uso de memória deve ser razoável (menos de 500MB para 50k transações)
                memoryUsed shouldBeLessThan 500L
                
                println("Memory used for ${totalBatches * batchSize} transactions: ${memoryUsed}MB")
            }
        }

        When("testando múltiplos períodos simultaneamente") {
            val aggregatorService = TransactionAggregatorService()
            val periodsCount = 24 // Simular 24 horas
            val transactionsPerPeriod = 1000
            val periods = mutableSetOf<String>()

            // Simular transações em diferentes períodos
            repeat(periodsCount) { hour ->
                val period = "2024-01-15-${hour.toString().padStart(2, '0')}"
                periods.add(period)
                
                repeat(transactionsPerPeriod) { i ->
                    val transaction = generateRandomTransactionForPeriod(period, i)
                    aggregatorService.addTransaction(transaction)
                }
            }

            Then("deve manter separação correta por período") {
                val bufferStatus = aggregatorService.getBufferStatus()
                
                // Verificar que todos os períodos estão presentes
                bufferStatus.keys.size shouldBe periodsCount
                
                // Verificar que cada período tem o número correto de transações
                bufferStatus.values.forEach { count ->
                    count shouldBe transactionsPerPeriod
                }
                
                // Gerar resumo para um período específico
                val testPeriod = "2024-01-15-12"
                val summary = aggregatorService.generateSummary(testPeriod)
                summary shouldNotBe null
                summary!!.totalTransactions shouldBe transactionsPerPeriod.toLong()
                summary.period shouldBe testPeriod
            }
        }
    }
})

private fun generateRandomTransaction(id: Int): Transaction {
    val accountIds = listOf("acc-001", "acc-002", "acc-003", "acc-004", "acc-005")
    val transactionTypes = TransactionType.values()
    val pixKeyTypes = PixKeyType.values()
    
    val type = transactionTypes[Random.nextInt(transactionTypes.size)]
    val accountId = accountIds[Random.nextInt(accountIds.size)]
    val amount = BigDecimal(Random.nextDouble(10.0, 5000.0)).setScale(2, java.math.RoundingMode.HALF_UP)
    
    val pixData = if (type == TransactionType.PIX) {
        val keyType = pixKeyTypes[Random.nextInt(pixKeyTypes.size)]
        val pixKey = when (keyType) {
            PixKeyType.EMAIL -> "user$id@example.com"
            PixKeyType.CPF -> (Random.nextLong(10000000000L, 99999999999L)).toString()
            PixKeyType.PHONE -> "+55119${Random.nextInt(10000000, 99999999)}"
            PixKeyType.CNPJ -> "${Random.nextLong(10000000000000L, 99999999999999L)}"
            PixKeyType.RANDOM -> "random-${Random.nextInt(100000, 999999)}"
        }
        PixData(pixKey, keyType, "E$id${System.currentTimeMillis()}")
    } else null
    
    return Transaction(
        id = "perf-txn-$id",
        accountId = accountId,
        amount = amount,
        type = type,
        description = "Performance test transaction $id",
        timestamp = LocalDateTime.now(),
        pixData = pixData
    )
}

private fun generateRandomTransactionForPeriod(period: String, id: Int): Transaction {
    // Parse period to set appropriate timestamp
    val (year, month, day, hour) = period.split("-").map { it.toInt() }
    val timestamp = LocalDateTime.of(year, month, day, hour, Random.nextInt(0, 59), Random.nextInt(0, 59))
    
    val transaction = generateRandomTransaction(id)
    return transaction.copy(
        id = "period-$period-$id",
        timestamp = timestamp
    )
}
