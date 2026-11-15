package com.aiinterview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.aiinterview.config.AppProperties;

@SpringBootApplication
@MapperScan("com.aiinterview.persistence.mapper")
@EnableConfigurationProperties(AppProperties.class)
public class InterviewApplication {
    public static void main(String[] args) { SpringApplication.run(InterviewApplication.class, args); }
}
