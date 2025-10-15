package br.com.itau.etl.producer.config

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.test.util.ReflectionTestUtils
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.ssm.SsmClient

class AwsConfigTest : DescribeSpec({

    describe("AwsConfig") {
        val awsConfig = AwsConfig()
        
        describe("s3Client") {
            context("when creating S3 client with default region") {
                it("should create S3Client with sa-east-1 region") {
                    // Given
                    ReflectionTestUtils.setField(awsConfig, "awsRegion", "sa-east-1")
                    
                    // When
                    val s3Client = awsConfig.s3Client()
                    
                    // Then
                    s3Client shouldNotBe null
                    s3Client shouldBe instanceOf<S3Client>()
                }
            }
            
            context("when creating S3 client with custom region") {
                it("should create S3Client with us-east-1 region") {
                    // Given
                    ReflectionTestUtils.setField(awsConfig, "awsRegion", "us-east-1")
                    
                    // When
                    val s3Client = awsConfig.s3Client()
                    
                    // Then
                    s3Client shouldNotBe null
                    s3Client shouldBe instanceOf<S3Client>()
                }
            }
        }

        describe("dynamoDbClient") {
            context("when creating DynamoDB client with default region") {
                it("should create DynamoDbClient with sa-east-1 region") {
                    // Given
                    ReflectionTestUtils.setField(awsConfig, "awsRegion", "sa-east-1")
                    
                    // When
                    val dynamoDbClient = awsConfig.dynamoDbClient()
                    
                    // Then
                    dynamoDbClient shouldNotBe null
                    dynamoDbClient shouldBe instanceOf<DynamoDbClient>()
                }
            }
            
            context("when creating DynamoDB client with custom region") {
                it("should create DynamoDbClient with us-west-2 region") {
                    // Given
                    ReflectionTestUtils.setField(awsConfig, "awsRegion", "us-west-2")
                    
                    // When
                    val dynamoDbClient = awsConfig.dynamoDbClient()
                    
                    // Then
                    dynamoDbClient shouldNotBe null
                    dynamoDbClient shouldBe instanceOf<DynamoDbClient>()
                }
            }
        }

        describe("ssmClient") {
            context("when creating SSM client with default region") {
                it("should create SsmClient with sa-east-1 region") {
                    // Given
                    ReflectionTestUtils.setField(awsConfig, "awsRegion", "sa-east-1")
                    
                    // When
                    val ssmClient = awsConfig.ssmClient()
                    
                    // Then
                    ssmClient shouldNotBe null
                    ssmClient shouldBe instanceOf<SsmClient>()
                }
            }
            
            context("when creating SSM client with custom region") {
                it("should create SsmClient with eu-west-1 region") {
                    // Given
                    ReflectionTestUtils.setField(awsConfig, "awsRegion", "eu-west-1")
                    
                    // When
                    val ssmClient = awsConfig.ssmClient()
                    
                    // Then
                    ssmClient shouldNotBe null
                    ssmClient shouldBe instanceOf<SsmClient>()
                }
            }
        }
    }
})
