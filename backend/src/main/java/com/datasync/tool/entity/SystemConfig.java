package com.datasync.tool.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Entity
@Table(name = "sys_config")
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfig {
    @Id
    private String configKey;
    
    @Column(length = 2000)
    private String configValue;
    
    private String configName;
    private String description;
}
