package shuhuai.badmintonflashbackend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import shuhuai.badmintonflashbackend.auth.RequireRole;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.enm.UserRole;
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
@RequireRole(value = UserRole.ADMIN, dbCheck = true)
public class AdminController {
    private final AdminService adminService;
    private final IUserService userService;

    @Autowired
    public AdminController(AdminService adminService, IUserService userService) {
        this.adminService = adminService;
        this.userService = userService;
    }

    @PatchMapping("/system")
    public Response<Void> updateSystemConfig(@RequestBody ConfigDTO configDTO) {
        adminService.updateConfig(configDTO);
        return new Response<>();
    }

    @PatchMapping("/system/{configKey}")
    public Response<Void> updateSystemConfigValue(@PathVariable("configKey") ConfigKey configKey,
                                                  @RequestBody String value) {
        adminService.updateConfig(new ConfigItemDTO(configKey, value));
        return new Response<>();
    }

    @GetMapping("/system")
    public Response<ConfigDTO> getSystemConfig() {
        return new Response<>(adminService.getConfig());
    }

    @GetMapping("/system/{configKey}")
    public Response<String> getSystemConfigValue(@PathVariable("configKey") ConfigKey configKey) {
        return new Response<>(adminService.getConfigValue(configKey));
    }

    @PostMapping("/session")
    public Response<Void> addSession(@RequestBody FlashSessionDTO flashSessionDTO) {
        adminService.addSession(flashSessionDTO);
        return new Response<>();
    }

    @PatchMapping("/session/{id}")
    public Response<Void> updateSession(@PathVariable("id") Integer id,
                                        @RequestBody FlashSessionDTO flashSessionDTO) {
        adminService.updateSession(id, flashSessionDTO);
        return new Response<>();
    }

    @DeleteMapping("/session/{id}")
    public Response<Void> deleteSession(@PathVariable("id") Integer id) {
        adminService.deleteSession(id);
        return new Response<>();
    }

    @PostMapping("/warmup/{sessionId}")
    public Response<Void> warmupSession(@PathVariable("sessionId") Integer sessionId) {
        adminService.warmupSession(sessionId);
        return new Response<>();
    }

    @PostMapping("/open/{sessionId}")
    public Response<Void> openSession(@PathVariable("sessionId") Integer sessionId) {
        adminService.openSession(sessionId);
        return new Response<>();
    }

    @PostMapping("/slot-gen/{sessionId}")
    public Response<Void> generateSlot(@PathVariable("sessionId") Integer sessionId) {
        adminService.generateSlot(sessionId);
        return new Response<>();
    }

    @GetMapping("/users")
    public Response<List<UserAccountVO>> listUsers() {
        return new Response<>(userService.listUsers());
    }

    @GetMapping("/users/{id}")
    public Response<UserAccountVO> getUser(@PathVariable("id") Integer id) {
        return new Response<>(userService.getUser(id));
    }

    @PostMapping("/users")
    public Response<Void> createUser(@RequestBody AdminUserDTO adminUserDTO) {
        Integer operatorUserId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        userService.adminCreateUser(operatorUserId, adminUserDTO);
        return new Response<>();
    }

    @PatchMapping("/users/{id}")
    public Response<Void> updateUser(@PathVariable("id") Integer id, @RequestBody AdminUserDTO adminUserDTO) {
        Integer operatorUserId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        userService.adminUpdateUser(operatorUserId, id, adminUserDTO);
        return new Response<>();
    }

    @DeleteMapping("/users/{id}")
    public Response<Void> deleteUser(@PathVariable("id") Integer id) {
        Integer operatorUserId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        userService.adminDeleteUser(operatorUserId, id);
        return new Response<>();
    }
}
