package br.com.itau.etl.producer.service

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.test.util.ReflectionTestUtils
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest
import software.amazon.awssdk.services.ssm.model.GetParameterResponse
import software.amazon.awssdk.services.ssm.model.Parameter
import java.math.BigDecimal

class ConfigurationServiceTest : DescribeSpec({

    describe("ConfigurationService") {
        val ssmClient = mockk<SsmClient>()
        val configurationService = ConfigurationService()
        
        beforeEach {
            ReflectionTestUtils.setField(configurationService, "ssmClient", ssmClient)
            ReflectionTestUtils.setField(configurationService, "projectName", "test-project")
            clearAllMocks()
        }

        describe("isPixEnabled") {
            
            context("when SSM returns 'true'") {
                it("should return true") {
                    // Given
                    val parameter = mockk<Parameter> {
                        every { value() } returns "true"
                    }
                    val response = mockk<GetParameterResponse> {
                        every { parameter() } returns parameter
                    }
                    every { ssmClient.getParameter(any<GetParameterRequest>()) } returns response
                    
                    // When
                    val result = configurationService.isPixEnabled()
                    
                    // Then
                    result shouldBe true
                    
                    verify {
                        ssmClient.getParameter(
                            match<GetParameterRequest> { 
                                it.name() == "/test-project/feature/acceptPIX"
                            }
                        )
                    }
                }
            }
            
            context("when SSM returns 'TRUE' (uppercase)") {
                it("should return true") {
                    // Given
                    val parameter = mockk<Parameter> {
                        every { value() } returns "TRUE"
                    }
                    val response = mockk<GetParameterResponse> {
                        every { parameter() } returns parameter
                    }
                    every { ssmClient.getParameter(any<GetParameterRequest>()) } returns response
                    
                    // When
                    val result = configurationService.isPixEnabled()
                    
                    // Then
                    result shouldBe true
                }
            }
            
            context("when SSM returns 'false'") {
                it("should return false") {
                    // Given
                    val parameter = mockk<Parameter> {
                        every { value() } returns "false"
                    }
                    val response = mockk<GetParameterResponse> {
                        every { parameter() } returns parameter
                    }
                    every { ssmClient.getParameter(any<GetParameterRequest>()) } returns response
                    
                    // When
                    val result = configurationService.isPixEnabled()
                    
                    // Then
                    result shouldBe false
                }
            }
            
            context("when SSM returns invalid value") {
                it("should return false") {
                    // Given
                    val parameter = mockk<Parameter> {
                        every { value() } returns "invalid"
                    }
                    val response = mockk<GetParameterResponse> {
                        every { parameter() } returns parameter
                    }
                    every { ssmClient.getParameter(any<GetParameterRequest>()) } returns response
                    
                    // When
                    val result = configurationService.isPixEnabled()
                    
                    // Then
                    result shouldBe false
                }
            }
            
            context("when SSM parameter is not found") {
                it("should return false and log error") {
                    // Given
                    every { ssmClient.getParameter(any<GetParameterRequest>()) } throws RuntimeException("Parameter not found")
                    
                    // When
                    val result = configurationService.isPixEnabled()
                    
                    // Then
                    result shouldBe false
                }
            }
            
            context("when SSM client throws exception") {
                it("should return false and log error") {
                    // Given
                    every { ssmClient.getParameter(any<GetParameterRequest>()) } throws RuntimeException("SSM error")
                    
                    // When
                    val result = configurationService.isPixEnabled()
                    
                    // Then
                    result shouldBe false
                }
            }
        }

        describe("getCircuitBreakerThreshold") {
            
            context("when SSM returns valid decimal value") {
                it("should return the decimal value") {
                    // Given
                    val parameter = mockk<Parameter> {
                        every { value() } returns "0.10"
                    }
                    val response = mockk<GetParameterResponse> {
                        every { parameter() } returns parameter
                    }
                    every { ssmClient.getParameter(any<GetParameterRequest>()) } returns response
                    
                    // When
                    val result = configurationService.getCircuitBreakerThreshold()
                    
                    // Then
                    result shouldBe BigDecimal("0.10")
                    
                    verify {
                        ssmClient.getParameter(
                            match<GetParameterRequest> { 
                                it.name() == "/test-project/feature/circuitBreakerThreshold"
                            }
                        )
                    }
                }
            }
            
            context("when SSM returns integer value") {
                it("should return the decimal equivalent") {
                    // Given
                    val parameter = mockk<Parameter> {
                        every { value() } returns "1"
                    }
                    val response = mockk<GetParameterResponse> {
                        every { parameter() } returns parameter
                    }
                    every { ssmClient.getParameter(any<GetParameterRequest>()) } returns response
                    
                    // When
                    val result = configurationService.getCircuitBreakerThreshold()
                    
                    // Then
                    result shouldBe BigDecimal("1")
                }
            }
            
            context("when SSM returns zero") {
                it("should return zero") {
                    // Given
                    val parameter = mockk<Parameter> {
                        every { value() } returns "0"
                    }
                    val response = mockk<GetParameterResponse> {
                        every { parameter() } returns parameter
                    }
                    every { ssmClient.getParameter(any<GetParameterRequest>()) } returns response
                    
                    // When
                    val result = configurationService.getCircuitBreakerThreshold()
                    
                    // Then
                    result shouldBe BigDecimal("0")
                }
            }
            
            context("when SSM returns invalid decimal value") {
                it("should return default value and log error") {
                    // Given
                    val parameter = mockk<Parameter> {
                        every { value() } returns "invalid-decimal"
                    }
                    val response = mockk<GetParameterResponse> {
                        every { parameter() } returns parameter
                    }
                    every { ssmClient.getParameter(any<GetParameterRequest>()) } returns response
                    
                    // When
                    val result = configurationService.getCircuitBreakerThreshold()
                    
                    // Then
                    result shouldBe BigDecimal("0.05")
                }
            }
            
            context("when SSM parameter is not found") {
                it("should return default value and log error") {
                    // Given
                    every { ssmClient.getParameter(any<GetParameterRequest>()) } throws RuntimeException("Parameter not found")
                    
                    // When
                    val result = configurationService.getCircuitBreakerThreshold()
                    
                    // Then
                    result shouldBe BigDecimal("0.05")
                }
            }
            
            context("when SSM client throws exception") {
                it("should return default value and log error") {
                    // Given
                    every { ssmClient.getParameter(any<GetParameterRequest>()) } throws RuntimeException("SSM error")
                    
                    // When
                    val result = configurationService.getCircuitBreakerThreshold()
                    
                    // Then
                    result shouldBe BigDecimal("0.05")
                }
            }
        }
    }
})
