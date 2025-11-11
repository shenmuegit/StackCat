package com.stackcat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication()
@MapperScan("com.stackcat.repository")
public class StackCatApplication {
    public static void main(String[] args) {
        SpringApplication.run(StackCatApplication.class, args);
    }
}

