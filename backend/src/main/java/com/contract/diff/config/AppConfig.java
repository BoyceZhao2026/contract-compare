package com.contract.diff.config;

import com.contract.diff.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AppConfig implements ApplicationRunner {

    @Autowired
    private FileService fileService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 应用启动时初始化存储目录
        fileService.initStorageDirectory();
        log.info("应用配置初始化完成");
    }
}