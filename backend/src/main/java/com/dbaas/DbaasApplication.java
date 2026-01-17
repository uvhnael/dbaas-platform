package com.dbaas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DBaaS Platform - Database as a Service
 * 
 * Main application entry point for the high-availability 
 * database management platform.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class DbaasApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbaasApplication.class, args);
    }
}
