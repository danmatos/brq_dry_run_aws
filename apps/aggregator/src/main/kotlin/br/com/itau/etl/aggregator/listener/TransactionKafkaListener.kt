package br.com.itau.etl.aggregator.listener

import br.com.itau.etl.aggregator.model.Transaction
import br.com.itau.etl.aggregator.service.TransactionAggregatorService
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
    private lateinit var transactionAggregatorService: TransactionAggregatorService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @KafkaListener(
        topics = ["\${app.kafka.topics.transactions}"],
        groupId = "\${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    fun consumeTransactionForAggregation(
        @Payload message: String,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
        acknowledgment: Acknowledgment
    ) {
        logger.debug { "Received message for aggregation from topic: $topic, partition: $partition, offset: $offset" }

        try {
            // Deserialize the transaction
            val transaction: Transaction = objectMapper.readValue(message)
            
            // Add to aggregation buffer
            transactionAggregatorService.addTransaction(transaction)

            // Acknowledge successful processing
            acknowledgment.acknowledge()
            logger.debug { "Successfully aggregated transaction ${transaction.id}" }

        } catch (e: Exception) {
            logger.error(e) { "Failed to process message for aggregation from topic: $topic, partition: $partition, offset: $offset" }
            
            // Acknowledge even failed messages to avoid blocking the consumer
            acknowledgment.acknowledge()
        }
    }
}
