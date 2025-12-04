package com.contract.diff;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.contract.diff.mapper")
public class ContractDiffApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContractDiffApplication.class, args);
    }

}