package shuhuai.badmintonflashbackend.controller;

import org.springframework.web.bind.annotation.*;
import shuhuai.badmintonflashbackend.model.dto.ChangePasswordDTO;
import shuhuai.badmintonflashbackend.model.dto.UserDTO;
import shuhuai.badmintonflashbackend.response.Response;
import shuhuai.badmintonflashbackend.service.IUserService;
import shuhuai.badmintonflashbackend.utils.TokenValidator;

@RestController
@RequestMapping("/user")
public class UserController extends BaseController {
    private final IUserService userService;
    private final TokenValidator tokenValidator;

    public UserController(IUserService userService, TokenValidator tokenValidator) {
        this.userService = userService;
        this.tokenValidator = tokenValidator;
    }

    @PostMapping("/register")
    public Response<String> register(@RequestBody UserDTO userDTO) {
        Integer userId = userService.register(userDTO.getStudentId(), userDTO.getPassword());
        String token = tokenValidator.getToken(userId);
        return new Response<>(token);
    }

    @PostMapping("/login")
    public Response<String> login(@RequestBody UserDTO userDTO) {
        Integer userId = userService.login(userDTO.getStudentId(), userDTO.getPassword());
        String token = tokenValidator.getToken(userId);
        return new Response<>(token);
    }

    @PatchMapping("/password")
    public Response<Void> changePassword(@RequestBody ChangePasswordDTO changePasswordDTO) {
        Integer userId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        userService.changePassword(userId, changePasswordDTO.getOldPassword(), changePasswordDTO.getNewPassword());
        return new Response<>();
    }

    @DeleteMapping
    public Response<Void> deleteUser() {
        Integer userId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        userService.deleteUser(userId);
        return new Response<>();
    }

}
