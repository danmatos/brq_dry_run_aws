package br.com.itau.etl.aggregator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableKafka
@EnableScheduling
class AggregatorApplication

fun main(args: Array<String>) {
    runApplication<AggregatorApplication>(*args)
}
