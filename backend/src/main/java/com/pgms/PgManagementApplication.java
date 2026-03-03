package com.pgms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class PgManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(PgManagementApplication.class, args);
    }
}
