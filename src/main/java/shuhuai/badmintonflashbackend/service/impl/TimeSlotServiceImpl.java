package shuhuai.badmintonflashbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shuhuai.badmintonflashbackend.entity.Court;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.entity.TimeSlot;
import shuhuai.badmintonflashbackend.excep.BaseException;
import shuhuai.badmintonflashbackend.mapper.ICourtMapper;
import shuhuai.badmintonflashbackend.mapper.ITimeSlotMapper;
import shuhuai.badmintonflashbackend.mapper.IFlashSessionMapper;
import shuhuai.badmintonflashbackend.response.ResponseCode;
import shuhuai.badmintonflashbackend.service.ITimeSlotService;
import shuhuai.badmintonflashbackend.utils.DateTimes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TimeSlotServiceImpl extends ServiceImpl<ITimeSlotMapper, TimeSlot> implements ITimeSlotService {
    private final IFlashSessionMapper flashSessionMapper;
    private final ICourtMapper courtMapper;
    private final ITimeSlotMapper timeSlotMapper;

    @Autowired
    public TimeSlotServiceImpl(IFlashSessionMapper flashSessionMapper, ICourtMapper courtMapper, ITimeSlotMapper timeSlotMapper) {
        this.flashSessionMapper = flashSessionMapper;
        this.courtMapper = courtMapper;
        this.timeSlotMapper = timeSlotMapper;
    }

    @Override
    @Transactional
    public void generateForDate(LocalDate date) {
        List<FlashSession> flashSessions = flashSessionMapper.selectList(null);
        for (FlashSession flashSession : flashSessions) {
            generateForDate(date, flashSession.getId());
        }
    }

    @Override
    @Transactional
    public void generateForDate(LocalDate date, Integer sessionId) {
        List<Court> courts = courtMapper.selectList(null);
        // 批量插入
        List<TimeSlot> timeSlots = new ArrayList<>();
        FlashSession flashSession = flashSessionMapper.selectById(sessionId);
        if (flashSession == null) {
            return;
        }
        // 校验整除
        long spanMin = Duration.between(flashSession.getBeginTime(), flashSession.getEndTime()).toMinutes();
        if (spanMin % flashSession.getSlotInterval() != 0) {
            throw new BaseException(ResponseCode.TIME_UNDEVIDED);
        }
        for (Court court : courts) {
            for (LocalTime t = flashSession.getBeginTime();
                 t.isBefore(flashSession.getEndTime());
                 t = t.plusMinutes(flashSession.getSlotInterval())) {
                TimeSlot timeSlot = new TimeSlot();
                timeSlot.setSlotDate(date);
                timeSlot.setStartTime(t);
                timeSlot.setEndTime(t.plusMinutes(flashSession.getSlotInterval()));
                timeSlot.setCourtId(court.getId());
                timeSlot.setSessionId(flashSession.getId());
                timeSlots.add(timeSlot);
            }
        }
        try {
            saveBatch(timeSlots);
        } catch (DuplicateKeyException e) {
            // 幂等：该日期+场次已生成过，视为成功
        }
    }

    @Override
    @Transactional
    public void addSessionHandler(Integer sessionId) {
        FlashSession flashSession = flashSessionMapper.selectById(sessionId);
        if (flashSession.getBeginTime().isBefore(DateTimes.nowTime())) {
            return;
        }
        generateForDate(DateTimes.nowDate(), sessionId);
    }

    @Override
    @Transactional
    public void updateSessionHandler(Integer sessionId) {
        // 先删除旧的
        deleteSessionHandler(sessionId);
        // 再添加新的
        addSessionHandler(sessionId);
    }

    @Override
    public void deleteSessionHandler(Integer sessionId) {
        FlashSession flashSession = flashSessionMapper.selectById(sessionId);
        // 如果早于现在就什么都不做
        if (flashSession.getBeginTime().isBefore(DateTimes.nowTime())) {
            return;
        }
        // 如果晚于
        timeSlotMapper.delete(new LambdaQueryWrapper<TimeSlot>().eq(TimeSlot::getSessionId, sessionId)
                .eq(TimeSlot::getSlotDate, DateTimes.nowDate()));
    }
}
