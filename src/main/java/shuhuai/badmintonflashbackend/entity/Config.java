package shuhuai.badmintonflashbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.model.dto.ConfigItemDTO;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Config {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private ConfigKey configKey;
    private String value;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "1", delval = "0")
    private Boolean isActive;

    private 

    public Config(ConfigItemDTO configItemDTO) {
        this.configKey = configItemDTO.getConfigKey();
        this.value = configItemDTO.getValue();
    }
}