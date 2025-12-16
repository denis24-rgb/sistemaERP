package com.erpservicio.servidor_descubrimiento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class ServidorDescubrimientoApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServidorDescubrimientoApplication.class, args);
	}

}
