package br.com.itau.etl.producer.config

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk

class MetricsConfigTest : DescribeSpec({

    describe("MetricsConfig") {
        val metricsConfig = MetricsConfig()
        val meterRegistry: MeterRegistry = SimpleMeterRegistry()

        describe("filesProcessedCounter") {
            context("when creating files processed counter") {
                it("should create Counter with correct name and description") {
                    // When
                    val counter = metricsConfig.filesProcessedCounter(meterRegistry)
                    
                    // Then
                    counter shouldNotBe null
                    counter.shouldBeInstanceOf<Counter>()
                    counter.id.name shouldBe "etl.producer.files.processed"
                    counter.id.description shouldBe "Number of files processed"
                }
            }
            
            context("when using files processed counter") {
                it("should increment counter correctly") {
                    // Given
                    val counter = metricsConfig.filesProcessedCounter(meterRegistry)
                    
                    // When
                    counter.increment()
                    counter.increment(2.0)
                    
                    // Then
                    counter.count() shouldBe 3.0
                }
            }
        }

        describe("filesRejectedCounter") {
            context("when creating files rejected counter") {
                it("should create Counter with correct name and description") {
                    // When
                    val counter = metricsConfig.filesRejectedCounter(meterRegistry)
                    
                    // Then
                    counter shouldNotBe null
                    counter.shouldBeInstanceOf<Counter>()
                    counter.id.name shouldBe "etl.producer.files.rejected"
                    counter.id.description shouldBe "Number of files rejected"
                }
            }
            
            context("when using files rejected counter") {
                it("should increment counter correctly") {
                    // Given
                    val counter = metricsConfig.filesRejectedCounter(meterRegistry)
                    
                    // When
                    counter.increment()
                    
                    // Then
                    counter.count() shouldBe 1.0
                }
            }
        }

        describe("transactionsValidatedCounter") {
            context("when creating transactions validated counter") {
                it("should create Counter with correct name and description") {
                    // When
                    val counter = metricsConfig.transactionsValidatedCounter(meterRegistry)
                    
                    // Then
                    counter shouldNotBe null
                    counter.shouldBeInstanceOf<Counter>()
                    counter.id.name shouldBe "etl.producer.transactions.validated"
                    counter.id.description shouldBe "Number of transactions validated"
                }
            }
            
            context("when using transactions validated counter") {
                it("should increment counter correctly") {
                    // Given
                    val counter = metricsConfig.transactionsValidatedCounter(meterRegistry)
                    
                    // When
                    counter.increment(5.0)
                    
                    // Then
                    counter.count() shouldBe 5.0
                }
            }
        }

        describe("transactionsSentCounter") {
            context("when creating transactions sent counter") {
                it("should create Counter with correct name and description") {
                    // When
                    val counter = metricsConfig.transactionsSentCounter(meterRegistry)
                    
                    // Then
                    counter shouldNotBe null
                    counter.shouldBeInstanceOf<Counter>()
                    counter.id.name shouldBe "etl.producer.transactions.sent"
                    counter.id.description shouldBe "Number of transactions sent to Kafka"
                }
            }
            
            context("when using transactions sent counter") {
                it("should increment counter correctly") {
                    // Given
                    val counter = metricsConfig.transactionsSentCounter(meterRegistry)
                    
                    // When
                    counter.increment(10.0)
                    
                    // Then
                    counter.count() shouldBe 10.0
                }
            }
        }

        describe("fileProcessingTimer") {
            context("when creating file processing timer") {
                it("should create Timer with correct name and description") {
                    // When
                    val timer = metricsConfig.fileProcessingTimer(meterRegistry)
                    
                    // Then
                    timer shouldNotBe null
                    timer.shouldBeInstanceOf<Timer>()
                    timer.id.name shouldBe "etl.producer.file.processing.time"
                    timer.id.description shouldBe "Time taken to process a file"
                }
            }
            
            context("when using file processing timer") {
                it("should record time correctly") {
                    // Given
                    val timer = metricsConfig.fileProcessingTimer(meterRegistry)
                    
                    // When
                    val sample = Timer.start(meterRegistry)
                    Thread.sleep(10) // Simulate some processing time
                    sample.stop(timer)
                    
                    // Then
                    timer.count() shouldBe 1
                    val totalTime = timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)
                    (totalTime > 0) shouldBe true
                }
            }
        }

        describe("metrics integration") {
            context("when using all metrics together") {
                it("should register all metrics in the same registry") {
                    // Given
                    val testRegistry = SimpleMeterRegistry()
                    
                    // When
                    val filesProcessedCounter = metricsConfig.filesProcessedCounter(testRegistry)
                    val filesRejectedCounter = metricsConfig.filesRejectedCounter(testRegistry)
                    val transactionsValidatedCounter = metricsConfig.transactionsValidatedCounter(testRegistry)
                    val transactionsSentCounter = metricsConfig.transactionsSentCounter(testRegistry)
                    val fileProcessingTimer = metricsConfig.fileProcessingTimer(testRegistry)
                    
                    // Then
                    testRegistry.meters.size shouldBe 5
                    testRegistry.find("etl.producer.files.processed").counter() shouldBe filesProcessedCounter
                    testRegistry.find("etl.producer.files.rejected").counter() shouldBe filesRejectedCounter
                    testRegistry.find("etl.producer.transactions.validated").counter() shouldBe transactionsValidatedCounter
                    testRegistry.find("etl.producer.transactions.sent").counter() shouldBe transactionsSentCounter
                    testRegistry.find("etl.producer.file.processing.time").timer() shouldBe fileProcessingTimer
                }
            }
        }
    }
})
