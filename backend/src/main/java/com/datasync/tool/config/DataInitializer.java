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
        initializeConfig("sys.logo", "", "系统LOGO", "系统LOGO图片的Base64 or URL");
        initializeConfig("sys.footer", "© 2025 Data Sync Tool. All rights reserved.", "页脚文字", "显示在页面底部的版权信息");
        initializeConfig("ops.export.path", "exports", "导出文件存储路径", "数据库运维导出文件的存放目录（相对或绝对路径）");
    }

    private void initializeConfig(String key, String value, String name, String desc) {
        if (systemConfigRepository.findById(key).isEmpty()) {
            systemConfigRepository.save(new SystemConfig(key, value, name, desc));
        }
    }
}
