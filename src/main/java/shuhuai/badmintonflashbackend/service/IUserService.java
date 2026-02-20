package shuhuai.badmintonflashbackend.service;

import shuhuai.badmintonflashbackend.enm.UserRole;

public interface IUserService {
    Integer register(String studentId, String password);

    Integer login(String studentId, String password);

    UserRole getRole(Integer userId);

    void changePassword(Integer userId, String oldPassword, String newPassword);

    void deleteUser(Integer userId);
}
