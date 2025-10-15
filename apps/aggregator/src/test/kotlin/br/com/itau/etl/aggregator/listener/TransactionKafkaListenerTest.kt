package br.com.itau.etl.aggregator.listener

import br.com.itau.etl.aggregator.model.PixData
import br.com.itau.etl.aggregator.model.PixKeyType
import br.com.itau.etl.aggregator.model.Transaction
import br.com.itau.etl.aggregator.model.TransactionType
import br.com.itau.etl.aggregator.service.TransactionAggregatorService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.*
import org.springframework.kafka.support.Acknowledgment
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDateTime

class TransactionKafkaListenerTest : BehaviorSpec({

    lateinit var kafkaListener: TransactionKafkaListener
    lateinit var aggregatorService: TransactionAggregatorService
    lateinit var objectMapper: ObjectMapper
    lateinit var acknowledgment: Acknowledgment

    beforeEach {
        aggregatorService = mockk()
        objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        acknowledgment = mockk(relaxed = true)
        
        kafkaListener = TransactionKafkaListener()
        ReflectionTestUtils.setField(kafkaListener, "transactionAggregatorService", aggregatorService)
        ReflectionTestUtils.setField(kafkaListener, "objectMapper", objectMapper)
    }

    Given("um listener Kafka para transações") {
        When("recebendo uma mensagem válida") {
            val transaction = createSampleTransaction()
            val messageJson = objectMapper.writeValueAsString(transaction)

            every { aggregatorService.addTransaction(any()) } just Runs

            kafkaListener.consumeTransactionForAggregation(
                message = messageJson,
                topic = "transactions",
                partition = 0,
                offset = 123L,
                acknowledgment = acknowledgment
            )

            Then("deve processar a transação corretamente") {
                verify { aggregatorService.addTransaction(match { 
                    it.id == transaction.id && 
                    it.accountId == transaction.accountId &&
                    it.amount == transaction.amount
                }) }
                
                verify { acknowledgment.acknowledge() }
            }
        }

        When("recebendo uma mensagem com JSON inválido") {
            val invalidJson = "{ invalid json }"

            kafkaListener.consumeTransactionForAggregation(
                message = invalidJson,
                topic = "transactions",
                partition = 0,
                offset = 123L,
                acknowledgment = acknowledgment
            )

            Then("deve fazer acknowledge mesmo com erro") {
                verify(exactly = 0) { aggregatorService.addTransaction(any()) }
                verify { acknowledgment.acknowledge() }
            }
        }

        When("o serviço de agregação lança exceção") {
            val transaction = createSampleTransaction()
            val messageJson = objectMapper.writeValueAsString(transaction)

            every { aggregatorService.addTransaction(any()) } throws RuntimeException("Service error")

            kafkaListener.consumeTransactionForAggregation(
                message = messageJson,
                topic = "transactions",
                partition = 0,
                offset = 123L,
                acknowledgment = acknowledgment
            )

            Then("deve fazer acknowledge para não bloquear o consumidor") {
                verify { aggregatorService.addTransaction(any()) }
                verify { acknowledgment.acknowledge() }
            }
        }

        When("processando diferentes tipos de transação") {
            val pixTransaction = createPixTransaction()
            val tedTransaction = createTedTransaction()
            
            val pixJson = objectMapper.writeValueAsString(pixTransaction)
            val tedJson = objectMapper.writeValueAsString(tedTransaction)

            every { aggregatorService.addTransaction(any()) } just Runs

            kafkaListener.consumeTransactionForAggregation(
                message = pixJson,
                topic = "transactions",
                partition = 0,
                offset = 123L,
                acknowledgment = acknowledgment
            )

            kafkaListener.consumeTransactionForAggregation(
                message = tedJson,
                topic = "transactions",
                partition = 1,
                offset = 124L,
                acknowledgment = acknowledgment
            )

            Then("deve processar ambos os tipos corretamente") {
                verify(exactly = 2) { aggregatorService.addTransaction(any()) }
                verify(exactly = 2) { acknowledgment.acknowledge() }
                
                // Verificar que PIX foi processado com dados PIX
                verify { aggregatorService.addTransaction(match { 
                    it.type == TransactionType.PIX && 
                    it.pixData != null 
                }) }
                
                // Verificar que TED foi processado sem dados PIX
                verify { aggregatorService.addTransaction(match { 
                    it.type == TransactionType.TED && 
                    it.pixData == null 
                }) }
            }
        }
    }
})

private fun createSampleTransaction(): Transaction {
    return Transaction(
        id = "txn-001",
        accountId = "acc-001",
        amount = BigDecimal("150.00"),
        type = TransactionType.PIX,
        description = "Sample PIX transaction",
        timestamp = LocalDateTime.of(2024, 1, 15, 14, 30, 0),
        pixData = PixData(
            pixKey = "user@example.com",
            pixKeyType = PixKeyType.EMAIL,
            endToEndId = "E123456789"
        )
    )
}

private fun createPixTransaction(): Transaction {
    return Transaction(
        id = "txn-pix-001",
        accountId = "acc-pix-001",
        amount = BigDecimal("250.00"),
        type = TransactionType.PIX,
        description = "PIX payment",
        timestamp = LocalDateTime.of(2024, 1, 15, 15, 0, 0),
        pixData = PixData(
            pixKey = "12345678901",
            pixKeyType = PixKeyType.CPF,
            endToEndId = "E987654321"
        )
    )
}

private fun createTedTransaction(): Transaction {
    return Transaction(
        id = "txn-ted-001",
        accountId = "acc-ted-001",
        amount = BigDecimal("1000.00"),
        type = TransactionType.TED,
        description = "TED transfer",
        timestamp = LocalDateTime.of(2024, 1, 15, 15, 15, 0),
        pixData = null
    )
}
