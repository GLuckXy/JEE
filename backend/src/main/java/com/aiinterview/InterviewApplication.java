package com.aiinterview;

import com.aiinterview.config.AppProperties;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan(basePackages = "com.aiinterview", annotationClass = Mapper.class)
@EnableConfigurationProperties(AppProperties.class)
@EnableAsync
public class InterviewApplication {
    public static void main(String[] args) { SpringApplication.run(InterviewApplication.class, args); }
}
