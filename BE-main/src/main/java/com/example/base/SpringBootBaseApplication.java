package com.example.base;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class SpringBootBaseApplication {

    public static void main(String[] args) {
        // PostgreSQL may not include the legacy timezone alias used by Java on Windows.
        if ("Asia/Saigon".equals(TimeZone.getDefault().getID())) {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        }
        SpringApplication.run(SpringBootBaseApplication.class, args);
    }
}
