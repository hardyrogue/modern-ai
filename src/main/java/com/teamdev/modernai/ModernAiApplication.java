package com.teamdev.modernai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.teamdev.modernai.mapper")
@SpringBootApplication
public class ModernAiApplication {



    public static void main(String[] args) {
        SpringApplication.run(ModernAiApplication.class, args);
    }

}
