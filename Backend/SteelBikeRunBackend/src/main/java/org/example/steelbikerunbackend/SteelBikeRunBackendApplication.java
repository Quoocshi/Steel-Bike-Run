package org.example.steelbikerunbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class SteelBikeRunBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SteelBikeRunBackendApplication.class, args);
        System.out.print("Hello");
    }

}
