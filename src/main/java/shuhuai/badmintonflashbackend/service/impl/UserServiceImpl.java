package shuhuai.badmintonflashbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.dao.DuplicateKeyException;
import shuhuai.badmintonflashbackend.enm.UserRole;
import shuhuai.badmintonflashbackend.excep.BaseException;
import shuhuai.badmintonflashbackend.response.ResponseCode;
import shuhuai.badmintonflashbackend.service.IUserService;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import shuhuai.badmintonflashbackend.entity.UserAccount;
import shuhuai.badmintonflashbackend.mapper.IUserAccountMapper;
import shuhuai.badmintonflashbackend.utils.HashComputer;

@Service
public class UserServiceImpl extends ServiceImpl<IUserAccountMapper, UserAccount> implements IUserService {
    private final IUserAccountMapper userAccountMapper;

    public UserServiceImpl(IUserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    @Override
    public Integer register(String studentId, String password) {
        if (studentId == null || studentId.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        UserAccount userAccount = new UserAccount();
        userAccount.setStudentId(studentId.trim());
        userAccount.setPassword(HashComputer.getHashedString(password));
        userAccount.setUserRole(UserRole.USER);
        try {
            userAccountMapper.insert(userAccount);
        } catch (DuplicateKeyException e) {
            // 依赖 DB 唯一键兜底，避免多实例并发下“先查后插”竞态
            throw new BaseException(ResponseCode.USER_DUPLICATED);
        }
        return userAccount.getId();
    }

    @Override
    public Integer login(String studentId, String password) {
        if (studentId == null || studentId.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        UserAccount userAccount = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getStudentId, studentId.trim())
                        .eq(UserAccount::getIsActive, true)
        );
        if (userAccount == null || !userAccount.getPassword().equals(HashComputer.getHashedString(password))) {
            throw new BaseException(ResponseCode.USERNAME_OR_PASSWORD_ERROR);
        }
        return userAccount.getId();
    }

    @Override
    public UserRole getRole(Integer userId) {
        if (userId == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        UserAccount userAccount = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getId, userId)
        );
        if (userAccount == null) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        UserRole userRole = userAccount.getUserRole();
        return userRole == null ? UserRole.USER : userRole;
    }

    @Override
    public void changePassword(Integer userId, String oldPassword, String newPassword) {
        if (userId == null || oldPassword == null || oldPassword.isEmpty() || newPassword == null || newPassword.isEmpty()) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        UserAccount userAccount = userAccountMapper.selectById(userId);
        if (userAccount == null || !Boolean.TRUE.equals(userAccount.getIsActive())) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        if (!userAccount.getPassword().equals(HashComputer.getHashedString(oldPassword))) {
            throw new BaseException(ResponseCode.USERNAME_OR_PASSWORD_ERROR);
        }
        userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                .set(UserAccount::getPassword, HashComputer.getHashedString(newPassword))
                .eq(UserAccount::getId, userId)
                .eq(UserAccount::getIsActive, true));
    }

    @Override
    public void deleteUser(Integer userId) {
        if (userId == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        UserAccount userAccount = userAccountMapper.selectById(userId);
        if (userAccount == null || !Boolean.TRUE.equals(userAccount.getIsActive())) {
            throw new BaseException(ResponseCode.TOKEN_INVALID);
        }
        userAccountMapper.deleteById(userId);
    }
}
