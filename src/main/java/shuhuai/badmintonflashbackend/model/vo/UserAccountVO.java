package shuhuai.badmintonflashbackend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import shuhuai.badmintonflashbackend.enm.UserRole;
import shuhuai.badmintonflashbackend.entity.UserAccount;

import java.time.LocalDateTime;

@Setter
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class UserAccountVO {
    private Integer id;
    private String studentId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private UserRole userRole;
    private Boolean isActive;

    public UserAccountVO(UserAccount userAccount) {
        this.id = userAccount.getId();
        this.studentId = userAccount.getStudentId();
        this.createTime = userAccount.getCreateTime();
        this.updateTime = userAccount.getUpdateTime();
        this.userRole = userAccount.getUserRole();
        this.isActive = userAccount.getIsActive();
    }
}
