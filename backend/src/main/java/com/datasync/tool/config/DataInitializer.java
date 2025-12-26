package com.datasync.tool.config;

import com.datasync.tool.entity.SystemConfig;
import com.datasync.tool.entity.User;
import com.datasync.tool.repository.SystemConfigRepository;
import com.datasync.tool.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        // Initialize default admin user
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setNickname("Administrator");
            admin.setRole("ADMIN");
            userRepository.save(admin);
        }

        // Initialize default system configs
        initializeConfig("sys.name", "Data Sync Tool", "系统名称", "显示在页面顶部的系统标题");
        initializeConfig("sys.logo", "", "系统LOGO", "系统LOGO图片的Base64或URL");
        initializeConfig("sys.footer", "© 2025 Data Sync Tool. All rights reserved.", "页脚文字", "显示在页面底部的版权信息");
    }

    private void initializeConfig(String key, String value, String name, String desc) {
        if (systemConfigRepository.findById(key).isEmpty()) {
            systemConfigRepository.save(new SystemConfig(key, value, name, desc));
        }
    }
}
