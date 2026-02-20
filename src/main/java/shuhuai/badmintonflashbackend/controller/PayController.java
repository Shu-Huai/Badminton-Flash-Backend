package shuhuai.badmintonflashbackend.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import shuhuai.badmintonflashbackend.auth.RequireRole;
import shuhuai.badmintonflashbackend.enm.UserRole;

@RestController
@RequestMapping("/pay")
@RequireRole(UserRole.USER)
public class PayController extends BaseController{
}
