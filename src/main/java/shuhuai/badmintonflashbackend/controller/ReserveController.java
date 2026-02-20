package shuhuai.badmintonflashbackend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import shuhuai.badmintonflashbackend.model.dto.ReserveDTO;
import shuhuai.badmintonflashbackend.response.Response;
import shuhuai.badmintonflashbackend.service.IReserveService;
import shuhuai.badmintonflashbackend.utils.TokenValidator;

@RestController
@RequestMapping("/reserve")
public class ReserveController extends BaseController {
    private final IReserveService reserveService;

    @Autowired
    public ReserveController(IReserveService reserveService) {
        this.reserveService = reserveService;
    }

    @PostMapping("/")
    public Response<Void> reserve(@RequestBody ReserveDTO reserveDTO) {
        Integer userId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        reserveService.reserve(userId, reserveDTO.getSlotId(), reserveDTO.getSessionId());
        return new Response<>();
    }
}
