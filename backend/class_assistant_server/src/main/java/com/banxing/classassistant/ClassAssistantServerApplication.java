package com.banxing.classassistant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.banxing.classassistant.mapper")
@SpringBootApplication
public class ClassAssistantServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClassAssistantServerApplication.class, args);
	}

}
