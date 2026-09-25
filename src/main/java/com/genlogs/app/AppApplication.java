package com.genlogs.app;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class AppApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppApplication.class, args);
    }

    @Bean
    CommandLineRunner generarHash() {
        return args -> {
            String hash = new BCryptPasswordEncoder().encode("Admin123*");
            System.out.println("==========================================");
            System.out.println("HASH REAL PARA Admin123*: " + hash);
            System.out.println("==========================================");
        };
    }
}
