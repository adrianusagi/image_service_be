package com.ituaku.image_service_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Restores your background task capabilities
public class ImageServiceApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImageServiceApiApplication.class, args);
    }
}