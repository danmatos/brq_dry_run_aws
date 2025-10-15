package br.com.itau.etl.aggregator.service

import br.com.itau.etl.aggregator.model.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.test.util.ReflectionTestUtils
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.math.BigDecimal
import java.time.LocalDateTime

class ReportServiceTest : BehaviorSpec({

    lateinit var reportService: ReportService
    lateinit var s3Client: S3Client
    lateinit var objectMapper: ObjectMapper

    beforeEach {
        s3Client = mockk()
        objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        
        reportService = ReportService()
        ReflectionTestUtils.setField(reportService, "s3Client", s3Client)
        ReflectionTestUtils.setField(reportService, "objectMapper", objectMapper)
        ReflectionTestUtils.setField(reportService, "reportsBucket", "test-reports-bucket")
    }

    Given("um serviço de relatórios") {
        When("salvando um relatório de resumo") {
            val summary = createSampleTransactionSummary()
            val putObjectRequestSlot = slot<PutObjectRequest>()
            val requestBodySlot = slot<RequestBody>()

            every { s3Client.putObject(capture(putObjectRequestSlot), capture(requestBodySlot)) } returns 
                PutObjectResponse.builder().build()

            reportService.saveReport(summary)

            Then("deve salvar o JSON no S3 com metadados corretos") {
                verify(exactly = 2) { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) }
                
                val jsonRequest = putObjectRequestSlot.captured
                jsonRequest.bucket() shouldBe "test-reports-bucket"
                jsonRequest.key() shouldContain "reports/"
                jsonRequest.key() shouldContain "transaction-summary-2024-01-15-14.json"
                jsonRequest.contentType() shouldBe "application/json"
                
                // Verificar metadados
                jsonRequest.metadata()["report-type"] shouldBe "transaction-summary"
                jsonRequest.metadata()["period"] shouldBe "2024-01-15-14"
                jsonRequest.metadata()["total-transactions"] shouldBe "10"
            }

            And("deve salvar também um CSV com os dados principais") {
                val allRequests = putObjectRequestSlot.allValues
                val csvRequest = allRequests.find { it.key().endsWith(".csv") }
                
                csvRequest shouldBe != null
                csvRequest!!.contentType() shouldBe "text/csv"
                csvRequest.key() shouldContain "transaction-summary-2024-01-15-14.csv"
            }
        }

        When("salvando um relatório detalhado") {
            val summary = createSampleTransactionSummary()
            val putObjectRequestSlot = slot<PutObjectRequest>()

            every { s3Client.putObject(capture(putObjectRequestSlot), any<RequestBody>()) } returns 
                PutObjectResponse.builder().build()

            reportService.saveDetailedReport(summary)

            Then("deve salvar CSV com detalhes das contas") {
                verify { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) }
                
                val request = putObjectRequestSlot.captured
                request.bucket() shouldBe "test-reports-bucket"
                request.key() shouldContain "top-accounts-2024-01-15-14.csv"
                request.contentType() shouldBe "text/csv"
            }
        }

        When("ocorre erro no S3") {
            val summary = createSampleTransactionSummary()
            
            every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } throws 
                RuntimeException("S3 Error")

            Then("deve propagar a exceção") {
                try {
                    reportService.saveReport(summary)
                    throw AssertionError("Deveria ter lançado exceção")
                } catch (e: Exception) {
                    e.message shouldBe "S3 Error"
                }
            }
        }
    }
})

private fun createSampleTransactionSummary(): TransactionSummary {
    return TransactionSummary(
        period = "2024-01-15-14",
        totalTransactions = 10,
        totalAmount = BigDecimal("5000.00"),
        transactionsByType = mapOf(
            TransactionType.PIX to TransactionTypeStats(
                count = 6,
                totalAmount = BigDecimal("3000.00"),
                averageAmount = BigDecimal("500.00")
            ),
            TransactionType.TED to TransactionTypeStats(
                count = 4,
                totalAmount = BigDecimal("2000.00"),
                averageAmount = BigDecimal("500.00")
            )
        ),
        topAccounts = listOf(
            AccountStats(
                accountId = "acc-001",
                transactionCount = 5,
                totalAmount = BigDecimal("2500.00")
            ),
            AccountStats(
                accountId = "acc-002",
                transactionCount = 3,
                totalAmount = BigDecimal("1500.00")
            ),
            AccountStats(
                accountId = "acc-003",
                transactionCount = 2,
                totalAmount = BigDecimal("1000.00")
            )
        ),
        pixStats = PixStats(
            totalPixTransactions = 6,
            totalPixAmount = BigDecimal("3000.00"),
            pixByKeyType = mapOf(
                PixKeyType.EMAIL to 3L,
                PixKeyType.CPF to 2L,
                PixKeyType.PHONE to 1L
            )
        ),
        generatedAt = LocalDateTime.of(2024, 1, 15, 14, 30, 0)
    )
}
