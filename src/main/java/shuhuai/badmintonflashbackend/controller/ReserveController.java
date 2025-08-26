package shuhuai.badmintonflashbackend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import shuhuai.badmintonflashbackend.response.Response;

@RestController
@RequestMapping("/reserve")
public class ReserveController {
    @PostMapping("/")
    public Response<Void> reserve(@RequestParam Integer userId, @RequestParam Integer slotId,
                                  @RequestParam Integer sessionId) {
        return new Response<>();
    }
}
