package com.irons.schismbackend;

import com.irons.schismbackend.core.engine.CodeGenerator;
import com.irons.schismbackend.core.model.GameCode;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SchismBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchismBackendApplication.class, args);
    }
}
