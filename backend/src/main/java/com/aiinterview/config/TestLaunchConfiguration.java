package com.aiinterview.config;

import com.aiinterview.content.service.TeachingContentService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;

@Configuration(proxyBeanMethods = false)
@Profile({"demo", "team-test"})
class TestLaunchConfiguration {
    @Bean(destroyMethod = "close")
    HikariDataSource testLaunchDataSource(ConfigurableEnvironment environment) {
        // Use only the selected mode's JDBC settings, even if Hikari environment overrides exist.
        var settings = environment.getPropertySources().get(TestLaunchEnvironment.PROPERTY_SOURCE);
        var config = new HikariConfig();
        config.setJdbcUrl((String) settings.getProperty("spring.datasource.url"));
        config.setUsername((String) settings.getProperty("spring.datasource.username"));
        config.setPassword((String) settings.getProperty("spring.datasource.password"));
        config.setDriverClassName((String) settings.getProperty("spring.datasource.driver-class-name"));
        return new HikariDataSource(config);
    }

    @Bean
    @Profile("demo")
    ApplicationRunner demoTeachingContent(TeachingContentService teachingContent) {
        return args -> teachingContent.install();
    }
}
