package shuhuai.badmintonflashbackend.controller;

import org.springframework.web.bind.annotation.*;
import shuhuai.badmintonflashbackend.auth.RequireRole;
import shuhuai.badmintonflashbackend.enm.UserRole;
import shuhuai.badmintonflashbackend.model.dto.UserSelfUpdateDTO;
import shuhuai.badmintonflashbackend.model.vo.UserAccountVO;
import shuhuai.badmintonflashbackend.response.Response;
import shuhuai.badmintonflashbackend.service.IUserService;
import shuhuai.badmintonflashbackend.utils.TokenValidator;

@RestController
@RequestMapping("/user")
@RequireRole(UserRole.USER)
public class UserController extends BaseController {
    private final IUserService userService;

    public UserController(IUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public Response<UserAccountVO> getMe() {
        Integer userId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        return new Response<>(userService.getMe(userId));
    }

    @PatchMapping("/me")
    public Response<Void> updateMe(@RequestBody UserSelfUpdateDTO userSelfUpdateDTO) {
        Integer userId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        userService.updateMe(userId, userSelfUpdateDTO);
        return new Response<>();
    }

    @DeleteMapping("/me")
    public Response<Void> deleteMe() {
        Integer userId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        userService.deleteMe(userId);
        return new Response<>();
    }

}
