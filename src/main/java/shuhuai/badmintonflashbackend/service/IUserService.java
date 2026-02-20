package shuhuai.badmintonflashbackend.service;

import shuhuai.badmintonflashbackend.enm.UserRole;
import shuhuai.badmintonflashbackend.model.dto.AdminUserDTO;
import shuhuai.badmintonflashbackend.model.dto.UserSelfUpdateDTO;
import shuhuai.badmintonflashbackend.model.vo.UserAccountVO;

import java.util.List;

public interface IUserService {
    Integer register(String studentId, String password);

    Integer login(String studentId, String password);

    UserRole getRole(Integer userId);

    UserAccountVO getMe(Integer userId);

    void updateMe(Integer userId, UserSelfUpdateDTO userSelfUpdateDTO);

    void deleteMe(Integer userId);

    List<UserAccountVO> listUsers();

    UserAccountVO getUser(Integer userId);

    void adminCreateUser(Integer operatorUserId, AdminUserDTO adminUserDTO);

    void adminUpdateUser(Integer operatorUserId, Integer targetUserId, AdminUserDTO adminUserDTO);

    void adminDeleteUser(Integer operatorUserId, Integer targetUserId);
}
