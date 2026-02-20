package shuhuai.badmintonflashbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import shuhuai.badmintonflashbackend.enm.UserRole;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserAccount {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String studentId;
    private String password;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private UserRole userRole;
    @TableLogic(value = "1", delval = "0")
    private Boolean isActive;
}
