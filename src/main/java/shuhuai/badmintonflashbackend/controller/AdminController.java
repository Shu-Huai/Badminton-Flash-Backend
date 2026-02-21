package shuhuai.badmintonflashbackend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import shuhuai.badmintonflashbackend.auth.RequireRole;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.enm.UserRole;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.model.dto.AdminUserDTO;
import shuhuai.badmintonflashbackend.model.dto.ConfigDTO;
import shuhuai.badmintonflashbackend.model.dto.ConfigItemDTO;
import shuhuai.badmintonflashbackend.model.dto.FlashSessionDTO;
import shuhuai.badmintonflashbackend.model.vo.UserAccountVO;
import shuhuai.badmintonflashbackend.response.Response;
import shuhuai.badmintonflashbackend.service.AdminService;
import shuhuai.badmintonflashbackend.service.IUserService;
import shuhuai.badmintonflashbackend.utils.TokenValidator;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequireRole(UserRole.USER)
public class AdminController {
    private final AdminService adminService;
    private final IUserService userService;

    @Autowired
    public AdminController(AdminService adminService, IUserService userService) {
        this.adminService = adminService;
        this.userService = userService;
    }

    @PatchMapping("/system")
    @RequireRole(UserRole.ADMIN)
    public Response<Void> updateSystemConfig(@RequestBody ConfigDTO configDTO) {
        adminService.updateConfig(configDTO);
        return new Response<>();
    }

    @PatchMapping("/system/{configKey}")
    @RequireRole(UserRole.ADMIN)
    public Response<Void> updateSystemConfigValue(@PathVariable ConfigKey configKey,
                                                  @RequestBody String value) {
        adminService.updateConfig(new ConfigItemDTO(configKey, value));
        return new Response<>();
    }

    @GetMapping("/system")
    @RequireRole(UserRole.ADMIN)
    public Response<ConfigDTO> getSystemConfig() {
        return new Response<>(adminService.getConfig());
    }

    @GetMapping("/system/{configKey}")
    @RequireRole(UserRole.ADMIN)
    public Response<String> getSystemConfigValue(@PathVariable ConfigKey configKey) {
        return new Response<>(adminService.getConfigValue(configKey));
    }

    @GetMapping("/session")
    public Response<List<FlashSession>> getSessions() {
        return new Response<>(adminService.getSessions());
    }

    @GetMapping("/session/{id}")
    public Response<FlashSession> getSession(@PathVariable Integer id) {
        return new Response<>(adminService.getSession(id));
    }

    @PostMapping("/session")
    @RequireRole(UserRole.ADMIN)
    public Response<Void> addSession(@RequestBody FlashSessionDTO flashSessionDTO) {
        adminService.addSession(flashSessionDTO);
        return new Response<>();
    }

    @PatchMapping("/session/{id}")
    @RequireRole(UserRole.ADMIN)
    public Response<Void> updateSession(@PathVariable Integer id,
                                        @RequestBody FlashSessionDTO flashSessionDTO) {
        adminService.updateSession(id, flashSessionDTO);
        return new Response<>();
    }

    @DeleteMapping("/session/{id}")
    @RequireRole(UserRole.ADMIN)
    public Response<Void> deleteSession(@PathVariable Integer id) {
        adminService.deleteSession(id);
        return new Response<>();
    }

    @PostMapping("/warmup/{sessionId}")
    @RequireRole(UserRole.ADMIN)
    public Response<Void> warmupSession(@PathVariable Integer sessionId) {
        adminService.warmupSession(sessionId);
        return new Response<>();
    }

    @PostMapping("/open/{sessionId}")
    @RequireRole(UserRole.ADMIN)
    public Response<Void> openSession(@PathVariable Integer sessionId) {
        adminService.openSession(sessionId);
        return new Response<>();
    }

    @PostMapping("/slot-gen/{sessionId}")
    @RequireRole(UserRole.ADMIN)
    public Response<Void> generateSlot(@PathVariable Integer sessionId) {
        adminService.generateSlot(sessionId);
        return new Response<>();
    }

    @GetMapping("/users")
    @RequireRole(UserRole.ADMIN)
    public Response<List<UserAccountVO>> listUsers() {
        return new Response<>(userService.listUsers());
    }

    @GetMapping("/users/{id}")
    @RequireRole(UserRole.ADMIN)
    public Response<UserAccountVO> getUser(@PathVariable Integer id) {
        return new Response<>(userService.getUser(id));
    }

    @PostMapping("/users")
    @RequireRole(UserRole.ADMIN)
    public Response<Void> createUser(@RequestBody AdminUserDTO adminUserDTO) {
        Integer operatorUserId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        userService.adminCreateUser(operatorUserId, adminUserDTO);
        return new Response<>();
    }

    @PatchMapping("/users/{id}")
    @RequireRole(UserRole.ADMIN)
    public Response<Void> updateUser(@PathVariable Integer id, @RequestBody AdminUserDTO adminUserDTO) {
        Integer operatorUserId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        userService.adminUpdateUser(operatorUserId, id, adminUserDTO);
        return new Response<>();
    }

    @DeleteMapping("/users/{id}")
    @RequireRole(UserRole.ADMIN)
    public Response<Void> deleteUser(@PathVariable Integer id) {
        Integer operatorUserId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        userService.adminDeleteUser(operatorUserId, id);
        return new Response<>();
    }
}
