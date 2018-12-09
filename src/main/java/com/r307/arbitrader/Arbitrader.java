package com.r307.arbitrader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class Arbitrader {
    public static void main(String... args) {
        SpringApplication.run(Arbitrader.class, args);
    }
}
