package shuhuai.badmintonflashbackend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import shuhuai.badmintonflashbackend.auth.RequireRole;
import shuhuai.badmintonflashbackend.enm.UserRole;
import shuhuai.badmintonflashbackend.model.dto.ReserveDTO;
import shuhuai.badmintonflashbackend.model.vo.ReserveResultVO;
import shuhuai.badmintonflashbackend.response.Response;
import shuhuai.badmintonflashbackend.service.IReserveService;
import shuhuai.badmintonflashbackend.utils.TokenValidator;

@RestController
@RequestMapping("/reserve")
@RequireRole(UserRole.USER)
public class ReserveController {
    private final IReserveService reserveService;

    @Autowired
    public ReserveController(IReserveService reserveService) {
        this.reserveService = reserveService;
    }

    @PostMapping({"", "/"})
    public Response<String> reserve(@RequestBody ReserveDTO reserveDTO) {
        Integer userId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        String traceId = reserveService.reserve(userId, reserveDTO.getSlotId(), reserveDTO.getSessionId());
        return new Response<>(traceId);
    }

    @GetMapping("/result/{traceId}")
    public Response<ReserveResultVO> getReserveResult(@PathVariable String traceId) {
        Integer userId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        return new Response<>(reserveService.getReserveResult(userId, traceId));
    }

    @DeleteMapping("/{reservationId}")
    public Response<Void> cancel(@PathVariable Integer reservationId) {
        Integer userId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        reserveService.cancel(userId, reservationId);
        return new Response<>();
    }
}
