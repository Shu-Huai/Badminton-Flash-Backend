package shuhuai.badmintonflashbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RSemaphore;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import shuhuai.badmintonflashbackend.constant.RedisKeys;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.entity.Config;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.entity.TimeSlot;
import shuhuai.badmintonflashbackend.excep.BaseException;
import shuhuai.badmintonflashbackend.mapper.ConfigMapper;
import shuhuai.badmintonflashbackend.mapper.IFlashSessionMapper;
import shuhuai.badmintonflashbackend.mapper.ITimeSlotMapper;
import shuhuai.badmintonflashbackend.model.dto.ConfigDTO;
import shuhuai.badmintonflashbackend.model.dto.ConfigItemDTO;
import shuhuai.badmintonflashbackend.model.dto.FlashSessionDTO;
import shuhuai.badmintonflashbackend.response.ResponseCode;
import shuhuai.badmintonflashbackend.service.AdminService;
import shuhuai.badmintonflashbackend.service.ITimeSlotService;
import shuhuai.badmintonflashbackend.utils.DateTimes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class AdminServiceImpl implements AdminService {
    private final ConfigMapper configMapper;
    private final IFlashSessionMapper sessionMapper;
    private final ITimeSlotService timeSlotService;
    private final ITimeSlotMapper timeSlotMapper;
    private final RedissonClient redisson;

    @Autowired
    public AdminServiceImpl(ConfigMapper configMapper, IFlashSessionMapper sessionMapper,
                            ITimeSlotService timeSlotService, ITimeSlotMapper timeSlotMapper, RedissonClient redisson) {
        this.configMapper = configMapper;
        this.sessionMapper = sessionMapper;
        this.timeSlotService = timeSlotService;
        this.timeSlotMapper = timeSlotMapper;
        this.redisson = redisson;
    }

    @Override
    @Transactional
    public void updateConfig(ConfigItemDTO configItemDTO) {
        if (configItemDTO == null || configItemDTO.getConfigKey() == null || configItemDTO.getValue() == null) {
            return;
        }
        updateConfig(new ConfigDTO(List.of(configItemDTO)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(ConfigDTO configDTO) {
        if (configDTO == null || configDTO.getConfigItems() == null || configDTO.getConfigItems().isEmpty()) {
            return;
        }

        int warmupMinutes;
        LocalTime generateTime;
        try {
            warmupMinutes = Integer.parseInt(getConfigValue(ConfigKey.WARMUP_MINUTE));
            generateTime = LocalTime.parse(getConfigValue(ConfigKey.GENERATE_TIME_SLOT_TIME));
        } catch (Exception e) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        for (ConfigItemDTO configItemDTO : configDTO.getConfigItems()) {
            if (configItemDTO == null || configItemDTO.getConfigKey() == null || configItemDTO.getValue() == null || configItemDTO.getValue().isEmpty()) {
                continue;
            }
            try {
                if (configItemDTO.getConfigKey() == ConfigKey.WARMUP_MINUTE) {
                    warmupMinutes = Integer.parseInt(configItemDTO.getValue());
                } else if (configItemDTO.getConfigKey() == ConfigKey.GENERATE_TIME_SLOT_TIME) {
                    generateTime = LocalTime.parse(configItemDTO.getValue());
                }
            } catch (Exception e) {
                throw new BaseException(ResponseCode.PARAM_ERROR);
            }
        }
        if (warmupMinutes < 0) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        List<FlashSession> sessions = sessionMapper.selectList(null);
        for (FlashSession session : sessions) {
            if (session == null || session.getFlashTime() == null || session.getBeginTime() == null
                    || session.getEndTime() == null || session.getSlotInterval() == null) {
                throw new BaseException(ResponseCode.PARAM_ERROR);
            }
            validConfigSession(warmupMinutes, generateTime, session);
        }

        Set<ConfigKey> changedKeys = new HashSet<>();
        for (ConfigItemDTO configItemDTO : configDTO.getConfigItems()) {
            if (configItemDTO.getConfigKey() == null || configItemDTO.getValue() == null || configItemDTO.getValue().isEmpty()) {
                continue;
            }
            Config current = configMapper.selectOne(new LambdaQueryWrapper<Config>()
                    .eq(Config::getConfigKey, configItemDTO.getConfigKey()));
            if (current != null && !Objects.equals(current.getValue(), configItemDTO.getValue())) {
                changedKeys.add(configItemDTO.getConfigKey());
            }
            configMapper.update(null, new LambdaUpdateWrapper<Config>()
                    .set(Config::getValue, configItemDTO.getValue())
                    .eq(Config::getConfigKey, configItemDTO.getConfigKey()));
        }
        if (changedKeys.isEmpty()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                triggerConfigCompensation(changedKeys);
            }
        });
    }

    private void validConfigSession(int warmupMinutes, LocalTime generateTime, FlashSession session) {
        if (!session.getBeginTime().isBefore(session.getEndTime())) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        if (session.getSlotInterval() <= 0) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        long spanMin = Duration.between(session.getBeginTime(), session.getEndTime()).toMinutes();
        if (spanMin % session.getSlotInterval() != 0) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        if (session.getFlashTime().isAfter(session.getBeginTime())) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        long flashMinutes = session.getFlashTime().toSecondOfDay() / 60;
        if (warmupMinutes < 0 || warmupMinutes > flashMinutes) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        LocalTime warmupTime = session.getFlashTime().minusMinutes(warmupMinutes);
        if (generateTime.isAfter(warmupTime) || warmupTime.isAfter(session.getFlashTime())) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
    }

    private void triggerConfigCompensation(Set<ConfigKey> changedKeys) {
        if (changedKeys.isEmpty()) {
            return;
        }
        if (changedKeys.contains(ConfigKey.GENERATE_TIME_SLOT_TIME)) {
            for (FlashSession flashSession : getSessions()) {
                generateSlot(flashSession.getId());
            }
        }
        if (changedKeys.contains(ConfigKey.WARMUP_MINUTE)) {
            int warmupMinutes = Integer.parseInt(getConfigValue(ConfigKey.WARMUP_MINUTE));
            LocalTime now = DateTimes.nowTime();
            for (FlashSession flashSession : getSessions()) {
                if (now.isBefore(flashSession.getFlashTime().minusMinutes(warmupMinutes))) {
                    continue;
                }
                warmupSession(flashSession);
            }
        }
    }

    @Override
    public ConfigDTO getConfig() {
        List<Config> configs = configMapper.selectList(null);
        return new ConfigDTO(configs.stream().map(ConfigItemDTO::new).toList());
    }

    @Override
    public String getConfigValue(ConfigKey configKey) {
        Config config = configMapper.selectOne(new LambdaQueryWrapper<Config>()
                .eq(Config::getConfigKey, configKey));
        return config.getValue();
    }

    @Override
    public void addSession(FlashSessionDTO flashSessionDTO) {
        FlashSession flashSession = new FlashSession(flashSessionDTO);
        int warmupMinutes;
        LocalTime generateTime;
        try {
            warmupMinutes = Integer.parseInt(getConfigValue(ConfigKey.WARMUP_MINUTE));
            generateTime = LocalTime.parse(getConfigValue(ConfigKey.GENERATE_TIME_SLOT_TIME));
        } catch (Exception e) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        if (flashSession.getFlashTime() == null || flashSession.getBeginTime() == null
                || flashSession.getEndTime() == null || flashSession.getSlotInterval() == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        validConfigSession(warmupMinutes, generateTime, flashSession);
        sessionMapper.insert(flashSession);
        timeSlotService.addSessionHandler(flashSession.getId());
    }

    @Override
    public void updateSession(Integer id, FlashSessionDTO flashSessionDTO) {
        FlashSession flashSession = new FlashSession(flashSessionDTO);
        int warmupMinutes;
        LocalTime generateTime;
        try {
            warmupMinutes = Integer.parseInt(getConfigValue(ConfigKey.WARMUP_MINUTE));
            generateTime = LocalTime.parse(getConfigValue(ConfigKey.GENERATE_TIME_SLOT_TIME));
        } catch (Exception e) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        if (flashSession.getFlashTime() == null || flashSession.getBeginTime() == null
                || flashSession.getEndTime() == null || flashSession.getSlotInterval() == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        validConfigSession(warmupMinutes, generateTime, flashSession);
        flashSession.setId(id);
        sessionMapper.updateById(flashSession);
        timeSlotService.updateSessionHandler(id);
    }

    @Override
    public void deleteSession(Integer id) {
        timeSlotService.deleteSessionHandler(id);
        sessionMapper.deleteById(id);
    }

    @Override
    public List<FlashSession> getSessions() {
        return sessionMapper.selectList(null);
    }

    @Override
    public FlashSession getSession(Integer id) {
        return sessionMapper.selectById(id);
    }

    @Override
    public void warmupSession(Integer sessionId) {
        FlashSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        warmupSession(session);
    }

    @Override
    public void warmupSession(FlashSession session) {
        LocalDate today = DateTimes.nowDate();
        long ttlSec = DateTimes.ttlToEndOfTodaySeconds();
        RBucket<String> sessionDone = redisson.getBucket(RedisKeys.warmupSessionDoneKey(today, session.getId()));
        if (sessionDone.isExists()) {
            return;
        }
        RLock lock = redisson.getLock(RedisKeys.warmupSessionLockKey(today, session.getId()));
        boolean locked;
        try {
            locked = lock.tryLock(0, 60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!locked) {
            return;
        }
        try {
            if (sessionDone.isExists()) {
                return;
            }
            List<TimeSlot> timeSlots = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()
                    .eq(TimeSlot::getSessionId, session.getId())
                    .eq(TimeSlot::getSlotDate, today));
            if (timeSlots.isEmpty()) {
                return;
            }
            for (TimeSlot timeSlot : timeSlots) {
                Integer slotId = timeSlot.getId();
                RBucket<Integer> slotSession = redisson.getBucket(RedisKeys.slotSessionKey(slotId));
                slotSession.set(session.getId(), Duration.ofSeconds(ttlSec));
                // 幂等：warmup:done:{slotId}
                RBucket<String> warmFlag = redisson.getBucket(RedisKeys.warmupDoneKey(slotId));
                boolean first = warmFlag.setIfAbsent("1", Duration.ofSeconds(ttlSec));
                if (!first) {
                    // 该 slot 已预热过，跳过
                    continue;
                }
                // semaphore: sem:{slotId}（许可=1；若你有容量字段可替换）
                RSemaphore semaphore = redisson.getSemaphore(RedisKeys.semKey(slotId));
                semaphore.trySetPermits(1);
                semaphore.expire(Duration.ofSeconds(ttlSec));
                // 3) 清空并设置 TTL（为确保存在后能设置 TTL，做一次 add/remove）
                RSet<Integer> dedup = redisson.getSet(RedisKeys.dedupKey(slotId));
                dedup.delete();                          // 清空
                dedup.add(-1);                           // 确保 key 存在
                dedup.expire(Duration.ofSeconds(ttlSec));
            }
            RBucket<String> gate = redisson.getBucket(RedisKeys.gateKey(session.getId()));
            if (!gate.isExists()) {
                gate.set("0", Duration.ofSeconds(ttlSec));
            }

            long startEpoch = ZonedDateTime
                    .of(DateTimes.nowDate(), session.getFlashTime(), DateTimes.zone())
                    .toEpochSecond();
            RBucket<Long> gateTime = redisson.getBucket(RedisKeys.gateTimeKey(session.getId()));
            gateTime.set(startEpoch, Duration.ofSeconds(ttlSec));
            sessionDone.set("1", Duration.ofSeconds(ttlSec));
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public void openSession(Integer sessionId) {
        FlashSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        long ttlSec = DateTimes.ttlToEndOfTodaySeconds();
        RBucket<String> gate = redisson.getBucket(RedisKeys.gateKey(session.getId()));
        gate.set("1", Duration.ofSeconds(ttlSec));
    }

    @Override
    public void generateSlot(Integer sessionId) {
        FlashSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        LocalDate today = DateTimes.nowDate();
        long ttl = Math.max(DateTimes.ttlToEndOfDaySeconds(today), 60L);

        RBucket<String> done = redisson.getBucket(RedisKeys.slotGenDoneKey(today, sessionId));
        if (done.isExists()) {
            return;
        }

        RLock lock = redisson.getLock(RedisKeys.slotGenLockKey(today, sessionId));
        boolean locked;
        try {
            locked = lock.tryLock(0, 120, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (!locked) {
            return;
        }
        try {
            if (done.isExists()) {
                return;
            }
            timeSlotService.generateForDate(today, sessionId);
            done.set("1", Duration.ofSeconds(ttl));
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
