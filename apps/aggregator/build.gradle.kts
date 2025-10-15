dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-logging")
    implementation("io.micrometer:micrometer-registry-cloudwatch2")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // AWS SDK
    implementation("software.amazon.awssdk:s3:2.25.11")
    implementation("software.amazon.awssdk:dynamodb:2.25.11")
    implementation("software.amazon.awssdk:ssm:2.25.11")
    implementation("software.amazon.awssdk:sts:2.25.11")
    
    // Kafka
    implementation("org.springframework.kafka:spring-kafka")
    implementation("software.amazon.msk:aws-msk-iam-auth:2.0.3")
    
    // JSON Processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
    
    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")
    implementation("com.github.loki4j:loki-logback-appender:2.0.0")
    
    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter")
    
    // Enhanced testing framework
    testImplementation("io.kotest:kotest-runner-junit5:6.0.4")
    testImplementation("io.kotest:kotest-assertions-core:6.0.4")
    testImplementation("io.kotest:kotest-property:6.0.4")
    testImplementation("io.kotest:kotest-framework-datatest:6.0.4")
    
    // Mocking
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    
    // TestContainers for integration testing
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:kafka:1.19.3")
    testImplementation("org.testcontainers:localstack:1.19.3")
    
    // Async testing support
    testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
}
