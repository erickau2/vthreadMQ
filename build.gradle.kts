plugins {
	java
	id("org.springframework.boot") version "4.0.0-SNAPSHOT"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.vthreadmq"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://repo.spring.io/milestone") }
	maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencies {
	// Spring Boot WebFlux for reactive programming
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-quartz")
	
	// WebSocket support
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	
	// JSON processing
	implementation("com.fasterxml.jackson.core:jackson-databind")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
	
	// Database support
	implementation("org.xerial:sqlite-jdbc:3.44.1.0")
	implementation("org.rocksdb:rocksdbjni:8.8.1")
	
	// Metrics and monitoring
	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("io.micrometer:micrometer-core")
	
	// Email support for plugins
	implementation("org.springframework.boot:spring-boot-starter-mail")
	
	// HTTP client for plugins
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	
	// Validation
	implementation("org.springframework.boot:spring-boot-starter-validation")
	
	// Lombok for cleaner code
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	
	// Development tools
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	
	// Testing
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("io.projectreactor:reactor-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
