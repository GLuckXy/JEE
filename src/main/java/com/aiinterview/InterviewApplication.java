package com.aiinterview;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.aiinterview.config.AppProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication(scanBasePackages = {"com.aiinterview", "cn.edu.interview.content", "com.example.interview",
        "edu.zjut.interview.formal", "com.aicoach.report.service.engine"})
@MapperScan({"com.aiinterview.persistence.mapper", "cn.edu.interview.content.mapper"})
@MapperScan(basePackages = "com.example.interview", annotationClass = org.apache.ibatis.annotations.Mapper.class)
@EnableConfigurationProperties(AppProperties.class)
@EnableAsync
public class InterviewApplication {
    public static void main(String[] args) { SpringApplication.run(InterviewApplication.class, args); }
}
