package kz.nurgissa.kasestockexchangeparser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KaseStockExchangeParserApplication {

	public static void main(String[] args) {
		SpringApplication.run(KaseStockExchangeParserApplication.class, args);
	}
}
