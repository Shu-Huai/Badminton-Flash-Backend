package shuhuai.badmintonflashbackend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shuhuai.badmintonflashbackend.model.dto.RefreshTokenDTO;
import shuhuai.badmintonflashbackend.model.dto.UserDTO;
import shuhuai.badmintonflashbackend.model.vo.AuthTokenVO;
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
    public Response<AuthTokenVO> register(@RequestBody UserDTO userDTO) {
        Integer userId = userService.register(userDTO.getStudentId(), userDTO.getPassword());
        String role = userService.getRole(userId).name();
        return new Response<>(new AuthTokenVO(
                tokenValidator.getAccessToken(userId, role),
                tokenValidator.getRefreshToken(userId)
        ));
    }

    @PostMapping("/login")
    public Response<AuthTokenVO> login(@RequestBody UserDTO userDTO) {
        Integer userId = userService.login(userDTO.getStudentId(), userDTO.getPassword());
        String role = userService.getRole(userId).name();
        return new Response<>(new AuthTokenVO(
                tokenValidator.getAccessToken(userId, role),
                tokenValidator.getRefreshToken(userId)
        ));
    }

    @PostMapping("/refresh")
    public Response<AuthTokenVO> refresh(@RequestBody RefreshTokenDTO refreshTokenDTO) {
        Integer userId = tokenValidator.verifyRefreshToken(refreshTokenDTO.getRefreshToken().trim());
        String role = userService.getRole(userId).name();
        return new Response<>(new AuthTokenVO(
                tokenValidator.getAccessToken(userId, role),
                tokenValidator.getRefreshToken(userId)
        ));
    }
}
