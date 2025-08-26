package shuhuai.badmintonflashbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class Court {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String courtName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "1", delval = "0")
    private Boolean isActive;
}
