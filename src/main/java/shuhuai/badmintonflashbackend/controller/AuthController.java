package shuhuai.badmintonflashbackend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shuhuai.badmintonflashbackend.model.dto.UserDTO;
import shuhuai.badmintonflashbackend.response.Response;
import shuhuai.badmintonflashbackend.service.IUserService;
import shuhuai.badmintonflashbackend.utils.TokenValidator;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final IUserService userService;
    private final TokenValidator tokenValidator;

    public AuthController(IUserService userService, TokenValidator tokenValidator) {
        this.userService = userService;
        this.tokenValidator = tokenValidator;
    }

    @PostMapping("/register")
    public Response<String> register(@RequestBody UserDTO userDTO) {
        Integer userId = userService.register(userDTO.getStudentId(), userDTO.getPassword());
        return new Response<>(tokenValidator.getToken(userId, userService.getRole(userId)));
    }

    @PostMapping("/login")
    public Response<String> login(@RequestBody UserDTO userDTO) {
        Integer userId = userService.login(userDTO.getStudentId(), userDTO.getPassword());
        return new Response<>(tokenValidator.getToken(userId, userService.getRole(userId)));
    }
}
