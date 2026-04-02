package com.strongwine.strongwine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class StrongwineApplication {

	public static void main(String[] args) {
		SpringApplication.run(StrongwineApplication.class, args);
	}

}
