package shuhuai.badmintonflashbackend.service;

public interface IUserService {
    Integer register(String studentId, String password);

    Integer login(String studentId, String password);

    void changePassword(Integer userId, String oldPassword, String newPassword);

    void deleteUser(Integer userId);
}
