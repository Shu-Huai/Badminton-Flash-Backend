package shuhuai.badmintonflashbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import shuhuai.badmintonflashbackend.constant.RedisKeys;
import shuhuai.badmintonflashbackend.enm.ReservationStatus;
import shuhuai.badmintonflashbackend.entity.Court;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.entity.Reservation;
import shuhuai.badmintonflashbackend.entity.TimeSlot;
import shuhuai.badmintonflashbackend.excep.BaseException;
import shuhuai.badmintonflashbackend.mapper.ICourtMapper;
import shuhuai.badmintonflashbackend.mapper.IFlashSessionMapper;
import shuhuai.badmintonflashbackend.mapper.IReservationMapper;
import shuhuai.badmintonflashbackend.mapper.ITimeSlotMapper;
import shuhuai.badmintonflashbackend.model.dto.ConditionBrowseSessionDTO;
import shuhuai.badmintonflashbackend.model.dto.ConditionBrowseSlotDTO;
import shuhuai.badmintonflashbackend.response.ResponseCode;
import shuhuai.badmintonflashbackend.service.IBrowseService;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
public class BrowseServiceImpl implements IBrowseService {
    private final IFlashSessionMapper sessionMapper;
    private final ICourtMapper courtMapper;
    private final ITimeSlotMapper timeSlotMapper;
    private final IReservationMapper reservationMapper;
    private final RedissonClient redisson;


    public BrowseServiceImpl(IFlashSessionMapper sessionMapper, ICourtMapper courtMapper, ITimeSlotMapper timeSlotMapper,
                             IReservationMapper reservationMapper, RedissonClient redisson) {
        this.sessionMapper = sessionMapper;
        this.courtMapper = courtMapper;
        this.timeSlotMapper = timeSlotMapper;
        this.reservationMapper = reservationMapper;
        this.redisson = redisson;
    }

    @Override
    public List<FlashSession> getSessions(ConditionBrowseSessionDTO dto) {
        if (dto == null) {
            return sessionMapper.selectList(null);
        }

        LambdaQueryWrapper<FlashSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.ge(dto.getFlashTimeLowerBound() != null, FlashSession::getFlashTime, dto.getFlashTimeLowerBound())
                .le(dto.getFlashTimeUpperBound() != null, FlashSession::getFlashTime, dto.getFlashTimeUpperBound())
                .ge(dto.getBeginTimeLowerBound() != null, FlashSession::getBeginTime, dto.getBeginTimeLowerBound())
                .le(dto.getBeginTimeUpperBound() != null, FlashSession::getBeginTime, dto.getBeginTimeUpperBound())
                .ge(dto.getEndTimeLowerBound() != null, FlashSession::getEndTime, dto.getEndTimeLowerBound())
                .le(dto.getEndTimeUpperBound() != null, FlashSession::getEndTime, dto.getEndTimeUpperBound())
                .ge(dto.getSlotIntervalLowerBound() != null,
                        FlashSession::getSlotInterval, dto.getSlotIntervalLowerBound())
                .le(dto.getSlotIntervalUpperBound() != null,
                        FlashSession::getSlotInterval, dto.getSlotIntervalUpperBound());
        return sessionMapper.selectList(queryWrapper);
    }

    @Override
    public FlashSession getSession(Integer id) {
        return sessionMapper.selectById(id);
    }

    @Override
    public List<Court> getCourts(String courtNameLike) {
        if (courtNameLike == null || courtNameLike.isEmpty()) {
            return courtMapper.selectList(new LambdaQueryWrapper<>());
        }
        return courtMapper.selectList(new LambdaQueryWrapper<Court>()
                .like(Court::getCourtName, courtNameLike));
    }

    @Override
    public Court getCourt(Integer id) {
        return courtMapper.selectById(id);
    }

    @Override
    public TimeSlot getSlot(Integer id) {
        return timeSlotMapper.selectById(id);
    }

    @Override
    public List<TimeSlot> getSlots(ConditionBrowseSlotDTO dto) {
        LambdaQueryWrapper<TimeSlot> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(TimeSlot::getSessionId, dto.getSessionId())
                .ge(dto.getDateLowerBound() != null, TimeSlot::getSlotDate, dto.getDateLowerBound())
                .le(dto.getDateUpperBound() != null, TimeSlot::getSlotDate, dto.getDateUpperBound())
                .ge(dto.getStartTimeLowerBound() != null, TimeSlot::getStartTime, dto.getStartTimeLowerBound())
                .le(dto.getStartTimeUpperBound() != null, TimeSlot::getStartTime, dto.getStartTimeUpperBound())
                .ge(dto.getEndTimeLowerBound() != null, TimeSlot::getEndTime, dto.getEndTimeLowerBound())
                .le(dto.getEndTimeUpperBound() != null, TimeSlot::getEndTime, dto.getEndTimeUpperBound())
                .in(dto.getCourtIds() != null, TimeSlot::getCourtId, dto.getCourtIds());
        return timeSlotMapper.selectList(queryWrapper);
    }

    @Override
    public List<Reservation> getReservations(Integer userId, Integer sessionId, Integer slotId, Set<ReservationStatus> statuses,
                                             LocalDate dateLowerBound, LocalDate dateUpperBound) {
        LambdaQueryWrapper<Reservation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Reservation::getUserId, userId);
        List<Integer> slotIds;
        if (slotId != null) {
            slotIds = List.of(slotId);
        } else {
            slotIds = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()
                    .eq(sessionId != null, TimeSlot::getSessionId, sessionId)
                    .ge(dateLowerBound != null, TimeSlot::getSlotDate, dateLowerBound)
                    .le(dateUpperBound != null, TimeSlot::getSlotDate, dateUpperBound)
                    .select(TimeSlot::getId)).stream().map(TimeSlot::getId).toList();
        }
        if (slotIds.isEmpty()) {
            return Collections.emptyList();
        }
        queryWrapper.in(Reservation::getSlotId, slotIds);
        queryWrapper.in(statuses != null, Reservation::getStatus, statuses);
        return reservationMapper.selectList(queryWrapper);
    }

    @Override
    public boolean isSessionOpen(Integer sessionId) {
        RBucket<String> gate = redisson.getBucket(RedisKeys.gateKey(sessionId));
        return "1".equals(gate.get());
    }
}
