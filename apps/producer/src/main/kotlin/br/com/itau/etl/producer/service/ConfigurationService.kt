package br.com.itau.etl.producer.service

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.GetParameterRequest
import java.math.BigDecimal

@Service
class ConfigurationService {

    private val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var ssmClient: SsmClient

    @Value("\${app.project-name}")
    private lateinit var projectName: String

    fun isPixEnabled(): Boolean {
        return try {
            val parameterName = "/$projectName/feature/acceptPIX"
            val request = GetParameterRequest.builder()
                .name(parameterName)
                .build()

            val response = ssmClient.getParameter(request)
            val value = response.parameter().value().lowercase()
            
            logger.debug { "PIX feature flag retrieved: $value" }
            value == "true"
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve PIX feature flag, defaulting to false" }
            false
        }
    }

    fun getCircuitBreakerThreshold(): BigDecimal {
        return try {
            val parameterName = "/$projectName/feature/circuitBreakerThreshold"
            val request = GetParameterRequest.builder()
                .name(parameterName)
                .build()

            val response = ssmClient.getParameter(request)
            val value = BigDecimal(response.parameter().value())
            
            logger.debug { "Circuit breaker threshold retrieved: $value" }
            value
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve circuit breaker threshold, defaulting to 0.05" }
            BigDecimal("0.05")
        }
    }
}
