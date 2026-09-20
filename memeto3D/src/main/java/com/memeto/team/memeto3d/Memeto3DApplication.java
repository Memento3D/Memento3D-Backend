package com.memeto.team.memeto3d;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Memeto3DApplication {

    public static void main(String[] args) {
        SpringApplication.run(Memeto3DApplication.class, args);
    }

}
