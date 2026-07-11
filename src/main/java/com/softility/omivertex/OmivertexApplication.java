package com.softility.omivertex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OmivertexApplication {

	public static void main(String[] args) {
		SpringApplication.run(OmivertexApplication.class, args);
	}

}
