package br.com.itau.etl.producer.service

import br.com.itau.etl.producer.model.ProcessingResult
import br.com.itau.etl.producer.model.Transaction
import br.com.itau.etl.producer.model.TransactionType
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class ValidationService {

    private val logger = KotlinLogging.logger {}

    @Autowired
    private lateinit var configurationService: ConfigurationService

    fun validate(transaction: Transaction): ProcessingResult {
        val errors = mutableListOf<String>()

        // Validações básicas
        if (transaction.amount <= BigDecimal.ZERO) {
            errors.add("Amount must be greater than zero")
        }

        if (transaction.accountId.isBlank()) {
            errors.add("Account ID cannot be blank")
        }

        // Validação específica para PIX
        if (transaction.type == TransactionType.PIX) {
            if (!configurationService.isPixEnabled()) {
                errors.add("PIX transactions are currently disabled")
            }

            transaction.pixData?.let { pixData ->
                if (pixData.pixKey.isBlank()) {
                    errors.add("PIX key cannot be blank")
                }
                if (pixData.endToEndId.isBlank()) {
                    errors.add("End-to-end ID cannot be blank")
                }
            } ?: errors.add("PIX data is required for PIX transactions")
        }

        // Validação de limites (exemplo)
        if (transaction.amount > BigDecimal("100000")) {
            errors.add("Amount exceeds maximum limit")
        }

        val isValid = errors.isEmpty()
        
        if (isValid) {
            logger.info { "Transaction ${transaction.id} validated successfully" }
        } else {
            logger.warn { "Transaction ${transaction.id} validation failed: ${errors.joinToString(", ")}" }
        }

        return ProcessingResult(transaction, isValid, errors)
    }
}
