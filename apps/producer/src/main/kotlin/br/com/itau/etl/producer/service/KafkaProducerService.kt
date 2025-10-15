package br.com.itau.etl.producer.service

import br.com.itau.etl.producer.model.Transaction
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class KafkaProducerService {

    private val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Value("\${app.kafka.topics.transactions}")
    private lateinit var transactionsTopic: String

    fun sendTransaction(transaction: Transaction) {
        try {
            val message = objectMapper.writeValueAsString(transaction)
            val future = kafkaTemplate.send(transactionsTopic, transaction.id, message)
            
            future.whenComplete { result, exception ->
                if (exception != null) {
                    logger.error(exception) { "Failed to send transaction ${transaction.id} to Kafka" }
                } else {
                    logger.info { "Transaction ${transaction.id} sent successfully to topic $transactionsTopic" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to serialize and send transaction ${transaction.id}" }
            throw e
        }
    }

    fun sendBatch(transactions: List<Transaction>) {
        transactions.forEach { transaction ->
            sendTransaction(transaction)
        }
        logger.info { "Sent batch of ${transactions.size} transactions to Kafka" }
    }
}
