package shuhuai.badmintonflashbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import shuhuai.badmintonflashbackend.enm.UserRole;
import shuhuai.badmintonflashbackend.entity.UserAccount;
import shuhuai.badmintonflashbackend.excep.BaseException;
import shuhuai.badmintonflashbackend.mapper.IUserAccountMapper;
import shuhuai.badmintonflashbackend.model.dto.AdminUserDTO;
import shuhuai.badmintonflashbackend.model.dto.UserSelfUpdateDTO;
import shuhuai.badmintonflashbackend.model.vo.UserAccountVO;
import shuhuai.badmintonflashbackend.response.ResponseCode;
import shuhuai.badmintonflashbackend.service.IUserService;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import shuhuai.badmintonflashbackend.utils.HashComputer;

import java.util.List;

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
        String normalizedStudentId = studentId.trim();
        UserAccount userAccount = new UserAccount();
        userAccount.setStudentId(normalizedStudentId);
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
        String normalizedStudentId = studentId.trim();
        UserAccount userAccount = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getStudentId, normalizedStudentId)
        );
        if (userAccount == null || !userAccount.getPassword().equals(HashComputer.getHashedString(password))) {
            throw new BaseException(ResponseCode.USERNAME_OR_PASSWORD_ERROR);
        }
        return userAccount.getId();
    }

    @Override
    public UserRole getRole(Integer userId) {
        UserAccount userAccount = requireActiveUser(userId);
        UserRole userRole = userAccount.getUserRole();
        return userRole == null ? UserRole.USER : userRole;
    }

    @Override
    public UserAccountVO getMe(Integer userId) {
        return new UserAccountVO(requireActiveUser(userId));
    }

    @Override
    public void updateMe(Integer userId, UserSelfUpdateDTO userSelfUpdateDTO) {
        if (userSelfUpdateDTO == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        UserAccount userAccount = requireActiveUser(userId);

        String studentId = userSelfUpdateDTO.getStudentId();
        if (studentId != null && !studentId.trim().isEmpty()) {
            userAccount.setStudentId(studentId.trim());
        }

        String oldPassword = userSelfUpdateDTO.getOldPassword();
        String newPassword = userSelfUpdateDTO.getNewPassword();
        boolean hasOld = oldPassword != null && !oldPassword.isEmpty();
        boolean hasNew = newPassword != null && !newPassword.isEmpty();
        if (hasOld ^ hasNew) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        if (hasOld) {
            if (!HashComputer.getHashedString(oldPassword).equals(userAccount.getPassword())) {
                throw new BaseException(ResponseCode.USERNAME_OR_PASSWORD_ERROR);
            }
            userAccount.setPassword(HashComputer.getHashedString(newPassword));
        }
        saveUser(userAccount);
    }

    @Override
    public void deleteMe(Integer userId) {
        UserAccount userAccount = requireActiveUser(userId);
        if (getRole(userAccount.getId()) == UserRole.ADMIN) {
            throw new BaseException(ResponseCode.FORBIDDEN);
        }
        userAccountMapper.deleteById(userId);
    }

    @Override
    public List<UserAccountVO> listUsers() {
        List<UserAccount> users = userAccountMapper.selectList(null);
        return users.stream().map(UserAccountVO::new).toList();
    }

    @Override
    public UserAccountVO getUser(Integer userId) {
        return new UserAccountVO(requireActiveUser(userId));
    }

    @Override
    public void adminCreateUser(Integer operatorUserId, AdminUserDTO adminUserDTO) {
        UserAccount operator = requireActiveUser(operatorUserId);
        if (getRole(operator.getId()) != UserRole.ADMIN) {
            throw new BaseException(ResponseCode.FORBIDDEN);
        }
        if (adminUserDTO == null
                || adminUserDTO.getStudentId() == null
                || adminUserDTO.getStudentId().trim().isEmpty()
                || adminUserDTO.getPassword() == null
                || adminUserDTO.getPassword().isEmpty()) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        UserAccount userAccount = new UserAccount();
        userAccount.setStudentId(adminUserDTO.getStudentId().trim());
        userAccount.setPassword(HashComputer.getHashedString(adminUserDTO.getPassword()));
        userAccount.setUserRole(adminUserDTO.getUserRole() == null ? UserRole.USER : adminUserDTO.getUserRole());
        try {
            userAccountMapper.insert(userAccount);
        } catch (DuplicateKeyException e) {
            throw new BaseException(ResponseCode.USER_DUPLICATED);
        }
    }

    @Override
    public void adminUpdateUser(Integer operatorUserId, Integer targetUserId, AdminUserDTO adminUserDTO) {
        if (adminUserDTO == null || targetUserId == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        UserAccount operator = requireActiveUser(operatorUserId);
        UserAccount target = requireActiveUser(targetUserId);
        assertCanAdminUpdate(operator, target);

        if (adminUserDTO.getStudentId() != null && !adminUserDTO.getStudentId().trim().isEmpty()) {
            target.setStudentId(adminUserDTO.getStudentId().trim());
        }
        if (adminUserDTO.getPassword() != null && !adminUserDTO.getPassword().isEmpty()) {
            target.setPassword(HashComputer.getHashedString(adminUserDTO.getPassword()));
        }
        if (adminUserDTO.getUserRole() != null) {
            target.setUserRole(adminUserDTO.getUserRole());
        }
        saveUser(target);
    }

    @Override
    public void adminDeleteUser(Integer operatorUserId, Integer targetUserId) {
        if (targetUserId == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        UserAccount operator = requireActiveUser(operatorUserId);
        if (getRole(operator.getId()) != UserRole.ADMIN) {
            throw new BaseException(ResponseCode.FORBIDDEN);
        }
        UserAccount target = requireActiveUser(targetUserId);
        if (getRole(target.getId()) == UserRole.ADMIN) {
            throw new BaseException(ResponseCode.FORBIDDEN);
        }
        userAccountMapper.deleteById(targetUserId);
    }

    private UserAccount requireActiveUser(Integer userId) {
        if (userId == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        UserAccount userAccount = userAccountMapper.selectById(userId);
        if (userAccount == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        return userAccount;
    }

    private void assertCanAdminUpdate(UserAccount operator, UserAccount target) {
        if (getRole(operator.getId()) != UserRole.ADMIN) {
            throw new BaseException(ResponseCode.FORBIDDEN);
        }
        if (getRole(target.getId()) == UserRole.ADMIN && !operator.getId().equals(target.getId())) {
            throw new BaseException(ResponseCode.FORBIDDEN);
        }
    }

    private void saveUser(UserAccount userAccount) {
        try {
            userAccountMapper.updateById(userAccount);
        } catch (DuplicateKeyException e) {
            throw new BaseException(ResponseCode.USER_DUPLICATED);
        }
    }
}
