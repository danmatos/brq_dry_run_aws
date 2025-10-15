package br.com.itau.etl.consumer.listener

import br.com.itau.etl.consumer.model.Transaction
import br.com.itau.etl.consumer.service.TransactionProcessorService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Component

@Component
class TransactionKafkaListener {

    private val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var transactionProcessorService: TransactionProcessorService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @KafkaListener(
        topics = ["\${app.kafka.topics.transactions}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consumeTransaction(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        logger.info { "Received message from topic: $topic, partition: $partition, offset: $offset" }

        try {
            // Deserialize the transaction
            val transaction: Transaction = objectMapper.readValue(message)
            logger.debug { "Deserialized transaction: ${transaction.id}" }

            // Process the transaction
            transactionProcessorService.processTransaction(transaction)

            // Acknowledge successful processing
            acknowledgment.acknowledge()
            logger.info { "Successfully processed and acknowledged transaction ${transaction.id}" }

        } catch (e: Exception) {
            logger.error(e) { "Failed to process message from topic: $topic, partition: $partition, offset: $offset" }
            
            // For now, we acknowledge even failed messages to avoid infinite retries
            // In production, you might want to implement a dead letter queue
            acknowledgment.acknowledge()
        }
    }
}
