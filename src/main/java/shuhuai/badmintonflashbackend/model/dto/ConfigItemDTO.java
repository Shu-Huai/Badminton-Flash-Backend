package shuhuai.badmintonflashbackend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.entity.Config;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ConfigItemDTO {
    private ConfigKey configKey;
    private String value;

    public ConfigItemDTO(Config config) {
        this.configKey = config.getConfigKey();
        this.value = config.getValue();
    }
}