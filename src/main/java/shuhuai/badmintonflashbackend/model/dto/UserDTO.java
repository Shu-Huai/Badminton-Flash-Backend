package shuhuai.badmintonflashbackend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class UserDTO {
    private String studentId;
    private String password;
}
