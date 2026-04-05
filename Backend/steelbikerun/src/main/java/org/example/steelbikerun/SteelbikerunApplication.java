package org.example.steelbikerun;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class SteelbikerunApplication {

    public static void main(String[] args) {
        SpringApplication.run(SteelbikerunApplication.class, args);
        System.out.print("Hello");
    }

}
