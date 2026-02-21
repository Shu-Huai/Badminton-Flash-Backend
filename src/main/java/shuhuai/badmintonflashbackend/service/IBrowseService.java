package shuhuai.badmintonflashbackend.service;

import shuhuai.badmintonflashbackend.enm.ReservationStatus;
import shuhuai.badmintonflashbackend.entity.Court;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.entity.Reservation;
import shuhuai.badmintonflashbackend.entity.TimeSlot;
import shuhuai.badmintonflashbackend.model.dto.ConditionBrowseSessionDTO;
import shuhuai.badmintonflashbackend.model.dto.ConditionBrowseSlotDTO;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface IBrowseService {
    List<FlashSession> getSessions(ConditionBrowseSessionDTO conditionBrowseSessionDTO);

    FlashSession getSession(Integer id);

    List<Court> getCourts(String courtNameLike);

    Court getCourt(Integer id);

    TimeSlot getSlot(Integer id);

    List<TimeSlot> getSlots(ConditionBrowseSlotDTO conditionBrowseSlotDTO);

    List<Reservation> getReservations(Integer userId, Integer sessionId, Integer slotId, Set<ReservationStatus> statuses,
                                      LocalDate dateLowerBound, LocalDate dateUpperBound);
}
