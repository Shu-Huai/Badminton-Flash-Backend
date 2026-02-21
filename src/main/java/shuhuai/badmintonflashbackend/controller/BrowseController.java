package shuhuai.badmintonflashbackend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shuhuai.badmintonflashbackend.auth.RequireRole;
import shuhuai.badmintonflashbackend.enm.UserRole;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.response.Response;
import shuhuai.badmintonflashbackend.service.AdminService;

import java.util.List;

@RestController
@RequestMapping("/browse")
@RequireRole(value = UserRole.USER)
public class BrowseController {
    private final AdminService adminService;

    public BrowseController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/session")
    public Response<List<FlashSession>> getSessions() {
        return new Response<>(adminService.getSessions());
    }

    @GetMapping("/session/{id}")
    public Response<FlashSession> getSession(@PathVariable("id") Integer id) {
        return new Response<>(adminService.getSession(id));
    }


}
