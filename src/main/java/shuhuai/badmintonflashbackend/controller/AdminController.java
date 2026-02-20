package shuhuai.badmintonflashbackend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.model.dto.ConfigDTO;
import shuhuai.badmintonflashbackend.model.dto.ConfigItemDTO;
import shuhuai.badmintonflashbackend.model.dto.FlashSessionDTO;
import shuhuai.badmintonflashbackend.response.Response;
import shuhuai.badmintonflashbackend.service.AdminService;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController extends BaseController {
    private final AdminService adminService;

    @Autowired
    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PatchMapping("/system")
    public Response<Void> updateSystemConfig(@RequestBody ConfigDTO configDTO) {
        adminService.updateConfig(configDTO);
        return new Response<>();
    }

    @PatchMapping("/system/{configKey}")
    public Response<Void> updateSystemConfigValue(@PathVariable(value = "configKey") ConfigKey configKey,
                                                  @RequestBody String value) {
        adminService.updateConfig(new ConfigItemDTO(configKey, value));
        return new Response<>();
    }

    @GetMapping("/system")
    public Response<ConfigDTO> getSystemConfig() {
        return new Response<>(adminService.getConfig());
    }

    @GetMapping("/system/{configKey}")
    public Response<String> getSystemConfigValue(@PathVariable(value = "configKey") ConfigKey configKey) {
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
    public Response<Void> addSession(@RequestBody FlashSessionDTO flashSessionDTO) {
        adminService.addSession(flashSessionDTO);
        return new Response<>();
    }

    @PatchMapping("/session/{id}")
    public Response<Void> updateSession(@PathVariable Integer id,
                                        @RequestBody FlashSessionDTO flashSessionDTO) {
        adminService.updateSession(id, flashSessionDTO);
        return new Response<>();
    }

    @DeleteMapping("/session/{id}")
    public Response<Void> deleteSession(@PathVariable Integer id) {
        adminService.deleteSession(id);
        return new Response<>();
    }

    @PostMapping("/warmup/{sessionId}")
    public Response<Void> warmupSession(@PathVariable Integer sessionId) {
        adminService.warmupSession(sessionId);
        return new Response<>();
    }

    @PostMapping("/open/{sessionId}")
    public Response<Void> openSession(@PathVariable Integer sessionId) {
        adminService.openSession(sessionId);
        return new Response<>();
    }

    @PostMapping("/slot-gen/{sessionId}")
    public Response<Void> generateSlot(@PathVariable Integer sessionId) {
        adminService.generateSlot(sessionId);
        return new Response<>();
    }
}
