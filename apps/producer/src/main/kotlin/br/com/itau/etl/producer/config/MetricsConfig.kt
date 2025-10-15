package br.com.itau.etl.producer.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MetricsConfig {

    @Bean
    fun filesProcessedCounter(meterRegistry: MeterRegistry): Counter {
        return Counter.builder("etl.producer.files.processed")
            .description("Number of files processed")
            .register(meterRegistry)
    }

    @Bean
    fun filesRejectedCounter(meterRegistry: MeterRegistry): Counter {
        return Counter.builder("etl.producer.files.rejected")
            .description("Number of files rejected")
            .register(meterRegistry)
    }

    @Bean
    fun transactionsValidatedCounter(meterRegistry: MeterRegistry): Counter {
        return Counter.builder("etl.producer.transactions.validated")
            .description("Number of transactions validated")
            .register(meterRegistry)
    }

    @Bean
    fun transactionsSentCounter(meterRegistry: MeterRegistry): Counter {
        return Counter.builder("etl.producer.transactions.sent")
            .description("Number of transactions sent to Kafka")
            .register(meterRegistry)
    }

    @Bean
    fun fileProcessingTimer(meterRegistry: MeterRegistry): Timer {
        return Timer.builder("etl.producer.file.processing.time")
            .description("Time taken to process a file")
            .register(meterRegistry)
    }
}
