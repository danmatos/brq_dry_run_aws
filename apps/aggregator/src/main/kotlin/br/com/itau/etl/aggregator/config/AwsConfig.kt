package br.com.itau.etl.aggregator.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

@Configuration
class AwsConfig {

    @Bean
    fun s3Client(): S3Client {
        return S3Client.builder()
            .credentialsProvider(DefaultCredentialsProvider.create())
            .region(Region.US_EAST_1) // Será sobrescrito pela variável de ambiente
            .build()
    }
}
