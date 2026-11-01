package dev.obe.mockcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MockCoreBankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockCoreBankingApplication.class, args);
    }
}
