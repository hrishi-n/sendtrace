package dev.sendtrace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SendTraceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SendTraceApplication.class, args);
    }
}
