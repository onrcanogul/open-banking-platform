package dev.obe.mockyos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MockYosApplication {

    public static void main(String[] args) {
        SpringApplication.run(MockYosApplication.class, args);
    }
}
