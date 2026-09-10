package io.github.mawsonlakes790913.chineseoutputforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class ChineseOutputForgeApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChineseOutputForgeApplication.class, args);
	}

}
