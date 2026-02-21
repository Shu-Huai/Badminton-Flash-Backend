package shuhuai.badmintonflashbackend.controller;

import org.springframework.web.bind.annotation.*;
import shuhuai.badmintonflashbackend.auth.RequireRole;
import shuhuai.badmintonflashbackend.enm.ReservationStatus;
import shuhuai.badmintonflashbackend.enm.UserRole;
import shuhuai.badmintonflashbackend.entity.Court;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.entity.Reservation;
import shuhuai.badmintonflashbackend.entity.TimeSlot;
import shuhuai.badmintonflashbackend.model.dto.ConditionBrowseSessionDTO;
import shuhuai.badmintonflashbackend.model.dto.ConditionBrowseSlotDTO;
import shuhuai.badmintonflashbackend.response.Response;
import shuhuai.badmintonflashbackend.service.IBrowseService;
import shuhuai.badmintonflashbackend.utils.TokenValidator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/browse")
@RequireRole(value = UserRole.USER)
public class BrowseController {
    private final IBrowseService browseService;

    public BrowseController(IBrowseService browseService) {
        this.browseService = browseService;
    }

    @GetMapping("/session")
    public Response<List<FlashSession>> getSessions(@RequestParam(required = false) LocalTime flashTimeLowerBound,
                                                    @RequestParam(required = false) LocalTime flashTimeUpperBound,
                                                    @RequestParam(required = false) LocalTime beginTimeLowerBound,
                                                    @RequestParam(required = false) LocalTime beginTimeUpperBound,
                                                    @RequestParam(required = false) LocalTime endTimeLowerBound,
                                                    @RequestParam(required = false) LocalTime endTimeUpperBound,
                                                    @RequestParam(required = false) Integer slotIntervalLowerBound,
                                                    @RequestParam(required = false) Integer slotIntervalUpperBound) {
        ConditionBrowseSessionDTO conditionBrowseSessionDTO = new ConditionBrowseSessionDTO(
                flashTimeLowerBound, flashTimeUpperBound, beginTimeLowerBound, beginTimeUpperBound,
                endTimeLowerBound, endTimeUpperBound, slotIntervalLowerBound, slotIntervalUpperBound);
        return new Response<>(browseService.getSessions(conditionBrowseSessionDTO));
    }

    @GetMapping("/session/{id}")
    public Response<FlashSession> getSession(@PathVariable Integer id) {
        return new Response<>(browseService.getSession(id));
    }

    @GetMapping("/court")
    public Response<List<Court>> getCourts(@RequestParam(required = false) String courtNameLike) {
        return new Response<>(browseService.getCourts(courtNameLike));
    }

    @GetMapping("/court/{id}")
    public Response<Court> getCourt(@PathVariable Integer id) {
        return new Response<>(browseService.getCourt(id));
    }

    @GetMapping("/slot/{id}")
    public Response<TimeSlot> getSlot(@PathVariable Integer id) {
        return new Response<>(browseService.getSlot(id));
    }

    @GetMapping("/slot")
    public Response<List<TimeSlot>> getSlots(@RequestParam Integer sessionId,
                                             @RequestParam(required = false) LocalDate dateLowerBound,
                                             @RequestParam(required = false) LocalDate dateUpperBound,
                                             @RequestParam(required = false) Set<Integer> courtIds,
                                             @RequestParam(required = false) LocalTime startTimeLowerBound,
                                             @RequestParam(required = false) LocalTime startTimeUpperBound,
                                             @RequestParam(required = false) LocalTime endTimeLowerBound,
                                             @RequestParam(required = false) LocalTime endTimeUpperBound) {
        ConditionBrowseSlotDTO conditionBrowseSlotDTO = new ConditionBrowseSlotDTO(
                sessionId, dateLowerBound, dateUpperBound, courtIds, startTimeLowerBound, startTimeUpperBound,
                endTimeLowerBound, endTimeUpperBound);
        return new Response<>(browseService.getSlots(conditionBrowseSlotDTO));
    }

    @GetMapping("/reservation")
    public Response<List<Reservation>> getReservations(@RequestParam(required = false) Integer sessionId,
                                                       @RequestParam(required = false) Integer slotId,
                                                       @RequestParam(required = false) Set<ReservationStatus> statuses,
                                                       @RequestParam(required = false) LocalDate dateLowerBound,
                                                       @RequestParam(required = false) LocalDate dateUpperBound) {
        Integer userId = Integer.parseInt(TokenValidator.getUser().get("userId"));
        return new Response<>(browseService.getReservations(userId, sessionId, slotId, statuses, dateLowerBound, dateUpperBound));
    }
}
