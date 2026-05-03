package com.mcit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.mcit")
@EnableScheduling
public class LawMIS {

	public static void main(String[] args) {
		SpringApplication.run(LawMIS.class, args);
	}

	

}
