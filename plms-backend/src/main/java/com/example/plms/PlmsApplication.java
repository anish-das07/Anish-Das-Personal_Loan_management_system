package com.example.plms;

import com.example.plms.common.PlmsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

/**
 * PLMS modular backend. Each top-level package (auth, customer, product, application, eligibility,
 * credit, document, approval, disbursement, account, notification, audit, report) is a module that
 * owns its tables and talks to other modules only through their service APIs, so modules can be
 * split into separate Spring Boot services behind the gateway (see infra/).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PlmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlmsApplication.class, args);
    }

    @Bean
    Clock clock(PlmsProperties properties) {
        return Clock.system(ZoneId.of(properties.timeZone()));
    }
}
