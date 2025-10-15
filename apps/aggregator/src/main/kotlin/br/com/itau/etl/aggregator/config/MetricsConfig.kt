package br.com.itau.etl.aggregator.config

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MetricsConfig {

    @Bean
    fun transactionsAggregatedCounter(meterRegistry: MeterRegistry): Counter {
        return Counter.builder("etl.aggregator.transactions.aggregated")
            .description("Number of transactions aggregated")
            .register(meterRegistry)
    }

    @Bean
    fun reportsGeneratedCounter(meterRegistry: MeterRegistry): Counter {
        return Counter.builder("etl.aggregator.reports.generated")
            .description("Number of reports generated")
            .register(meterRegistry)
    }

    @Bean
    fun reportGenerationTimer(meterRegistry: MeterRegistry): Timer {
        return Timer.builder("etl.aggregator.report.generation.time")
            .description("Time taken to generate a report")
            .register(meterRegistry)
    }
}
