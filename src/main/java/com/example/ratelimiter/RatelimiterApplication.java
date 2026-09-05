package com.example.ratelimiter;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RatelimiterApplication {

	public static void main(String[] args) {
		SpringApplication.run(RatelimiterApplication.class, args);
	}

	/**
	 * Injected into the limiters instead of calling {@code System.currentTimeMillis()} directly,
	 * which keeps their time-dependent logic testable with a fixed clock.
	 */
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
