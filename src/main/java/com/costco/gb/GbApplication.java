package com.costco.gb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GbApplication {

	public static void main(String[] args) {
		SpringApplication.run(GbApplication.class, args);
	}
}
