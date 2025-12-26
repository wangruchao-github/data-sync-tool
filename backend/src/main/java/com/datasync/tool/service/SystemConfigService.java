package com.datasync.tool.service;

import com.datasync.tool.entity.SystemConfig;
import com.datasync.tool.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    public List<SystemConfig> getAllConfigs() {
        return systemConfigRepository.findAll();
    }

    public Map<String, String> getPublicConfigs() {
        List<SystemConfig> configs = systemConfigRepository.findAll();
        return configs.stream()
                .filter(c -> c.getConfigKey().startsWith("sys."))
                .collect(Collectors.toMap(SystemConfig::getConfigKey, SystemConfig::getConfigValue));
    }

    @Transactional
    public void saveConfigs(List<SystemConfig> configs) {
        systemConfigRepository.saveAll(configs);
    }

    public String getConfig(String key, String defaultValue) {
        return systemConfigRepository.findById(key)
                .map(SystemConfig::getConfigValue)
                .orElse(defaultValue);
    }
}
