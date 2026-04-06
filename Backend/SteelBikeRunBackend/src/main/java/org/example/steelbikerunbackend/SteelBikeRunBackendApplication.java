package org.example.steelbikerunbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "org.example.steelbikerunbackend.module")
@EnableScheduling
public class SteelBikeRunBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SteelBikeRunBackendApplication.class, args);
    }

}
