package shuhuai.badmintonflashbackend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import shuhuai.badmintonflashbackend.enm.UserRole;

@Setter
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class AdminUserDTO {
    private String studentId;
    private String password;
    private UserRole userRole;
}
