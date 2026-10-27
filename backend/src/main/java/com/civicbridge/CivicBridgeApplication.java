package com.civicbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CivicBridgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(CivicBridgeApplication.class, args);
    }
}
