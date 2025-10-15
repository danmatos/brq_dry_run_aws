package br.com.itau.etl.consumer.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MetricsConfig {

    @Bean
    fun transactionsConsumedCounter(meterRegistry: MeterRegistry): Counter {
        return Counter.builder("etl.consumer.transactions.consumed")
            .description("Number of transactions consumed from Kafka")
            .register(meterRegistry)
    }

    @Bean
    fun transactionsProcessedCounter(meterRegistry: MeterRegistry): Counter {
        return Counter.builder("etl.consumer.transactions.processed")
            .description("Number of transactions successfully processed")
            .register(meterRegistry)
    }

    @Bean
    fun transactionsFailedCounter(meterRegistry: MeterRegistry): Counter {
        return Counter.builder("etl.consumer.transactions.failed")
            .description("Number of transactions that failed processing")
            .register(meterRegistry)
    }

    @Bean
    fun duplicateTransactionsCounter(meterRegistry: MeterRegistry): Counter {
        return Counter.builder("etl.consumer.transactions.duplicates")
            .description("Number of duplicate transactions detected")
            .register(meterRegistry)
    }

    @Bean
    fun transactionProcessingTimer(meterRegistry: MeterRegistry): Timer {
        return Timer.builder("etl.consumer.transaction.processing.time")
            .description("Time taken to process a transaction")
            .register(meterRegistry)
    }
}
