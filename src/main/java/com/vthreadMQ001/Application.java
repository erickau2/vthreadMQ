package com.vthreadMQ001;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {

	public static void main(String[] args) {
		// Enable virtual threads for the application
		System.setProperty("spring.threads.virtual.enabled", "true");
		
		SpringApplication.run(Application.class, args);
	}

}
