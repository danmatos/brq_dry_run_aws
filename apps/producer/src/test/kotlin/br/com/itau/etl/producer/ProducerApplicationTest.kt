package br.com.itau.etl.producer

import io.kotest.core.spec.style.DescribeSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.context.ApplicationContext
import org.springframework.beans.factory.annotation.Autowired
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.shouldBe
import br.com.itau.etl.producer.service.*
import br.com.itau.etl.producer.config.*

@SpringBootTest(classes = [ProducerApplication::class])
@ActiveProfiles("test")
class ProducerApplicationTest : DescribeSpec() {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    init {
        describe("ProducerApplication") {
            context("when application context loads") {
                it("should load successfully") {
                    // Then
                    applicationContext shouldNotBe null
                }
            }

            context("when checking required beans") {
                it("should have FileProcessorService bean") {
                    // When
                    val bean = applicationContext.getBean(FileProcessorService::class.java)
                    
                    // Then
                    bean shouldNotBe null
                }

                it("should have ValidationService bean") {
                    // When
                    val bean = applicationContext.getBean(ValidationService::class.java)
                    
                    // Then
                    bean shouldNotBe null
                }

                it("should have S3Service bean") {
                    // When
                    val bean = applicationContext.getBean(S3Service::class.java)
                    
                    // Then
                    bean shouldNotBe null
                }

                it("should have KafkaProducerService bean") {
                    // When
                    val bean = applicationContext.getBean(KafkaProducerService::class.java)
                    
                    // Then
                    bean shouldNotBe null
                }

                it("should have DynamoDbService bean") {
                    // When
                    val bean = applicationContext.getBean(DynamoDbService::class.java)
                    
                    // Then
                    bean shouldNotBe null
                }

                it("should have ConfigurationService bean") {
                    // When
                    val bean = applicationContext.getBean(ConfigurationService::class.java)
                    
                    // Then
                    bean shouldNotBe null
                }
            }

            context("when checking configuration beans") {
                it("should have AwsConfig bean") {
                    // When
                    val bean = applicationContext.getBean(AwsConfig::class.java)
                    
                    // Then
                    bean shouldNotBe null
                }

                it("should have KafkaProducerConfig bean") {
                    // When
                    val bean = applicationContext.getBean(KafkaProducerConfig::class.java)
                    
                    // Then
                    bean shouldNotBe null
                }

                it("should have MetricsConfig bean") {
                    // When
                    val bean = applicationContext.getBean(MetricsConfig::class.java)
                    
                    // Then
                    bean shouldNotBe null
                }
            }

            context("when checking Spring Boot features") {
                it("should have scheduling enabled") {
                    // Given
                    val schedulingAnnotation = ProducerApplication::class.java
                        .annotations
                        .find { it.annotationClass.simpleName == "EnableScheduling" }
                    
                    // Then
                    schedulingAnnotation shouldNotBe null
                }

                it("should be a Spring Boot application") {
                    // Given
                    val springBootAnnotation = ProducerApplication::class.java
                        .annotations
                        .find { it.annotationClass.simpleName == "SpringBootApplication" }
                    
                    // Then
                    springBootAnnotation shouldNotBe null
                }
            }
        }
    }
}
