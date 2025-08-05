package com.mcit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.mcit")
public class LawMIS {

	public static void main(String[] args) {
		SpringApplication.run(LawMIS.class, args);
	}

}
