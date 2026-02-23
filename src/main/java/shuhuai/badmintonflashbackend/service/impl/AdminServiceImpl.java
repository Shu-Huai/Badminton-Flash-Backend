package shuhuai.badmintonflashbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import shuhuai.badmintonflashbackend.entity.Court;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.entity.TimeSlot;
import shuhuai.badmintonflashbackend.excep.BaseException;
import shuhuai.badmintonflashbackend.mapper.IConfigMapper;
import shuhuai.badmintonflashbackend.mapper.ICourtMapper;
import shuhuai.badmintonflashbackend.mapper.IFlashSessionMapper;
import shuhuai.badmintonflashbackend.mapper.ITimeSlotMapper;
import shuhuai.badmintonflashbackend.model.dto.ConfigDTO;
import shuhuai.badmintonflashbackend.model.dto.ConfigItemDTO;
import shuhuai.badmintonflashbackend.model.dto.FlashSessionDTO;
import shuhuai.badmintonflashbackend.response.ResponseCode;
import shuhuai.badmintonflashbackend.service.IAdminService;
import shuhuai.badmintonflashbackend.service.ITimeSlotService;
import shuhuai.badmintonflashbackend.utils.DateTimes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class AdminServiceImpl implements IAdminService {
    private static final String DEFAULT_COURT_NAME_FORMAT = "球场%d";
    private static final Logger log = LoggerFactory.getLogger(AdminServiceImpl.class);

    private final IConfigMapper configMapper;
    private final ICourtMapper courtMapper;
    private final IFlashSessionMapper sessionMapper;
    private final ITimeSlotService timeSlotService;
    private final ITimeSlotMapper timeSlotMapper;
    private final RedissonClient redisson;

    @Autowired
    public AdminServiceImpl(IConfigMapper configMapper, ICourtMapper courtMapper, IFlashSessionMapper sessionMapper,
                            ITimeSlotService timeSlotService, ITimeSlotMapper timeSlotMapper, RedissonClient redisson) {
        this.configMapper = configMapper;
        this.courtMapper = courtMapper;
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
        int courtCount;
        String courtNameFormat;
        try {
            warmupMinutes = Integer.parseInt(getConfigValue(ConfigKey.WARMUP_MINUTE));
            generateTime = LocalTime.parse(getConfigValue(ConfigKey.GENERATE_TIME_SLOT_TIME));
            courtCount = getConfigIntOrDefault(ConfigKey.COURT_COUNT, countActiveCourts());
            courtNameFormat = getConfigStringOrDefault(ConfigKey.COURT_NAME_FORMAT, DEFAULT_COURT_NAME_FORMAT);
        } catch (Exception e) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        int previousCourtCount = courtCount;
        String previousCourtNameFormat = courtNameFormat;
        for (ConfigItemDTO configItemDTO : configDTO.getConfigItems()) {
            if (configItemDTO == null || configItemDTO.getConfigKey() == null || configItemDTO.getValue() == null || configItemDTO.getValue().isEmpty()) {
                continue;
            }
            try {
                if (configItemDTO.getConfigKey() == ConfigKey.WARMUP_MINUTE) {
                    warmupMinutes = Integer.parseInt(configItemDTO.getValue());
                } else if (configItemDTO.getConfigKey() == ConfigKey.GENERATE_TIME_SLOT_TIME) {
                    generateTime = LocalTime.parse(configItemDTO.getValue());
                } else if (configItemDTO.getConfigKey() == ConfigKey.COURT_COUNT) {
                    courtCount = Integer.parseInt(configItemDTO.getValue());
                } else if (configItemDTO.getConfigKey() == ConfigKey.COURT_NAME_FORMAT) {
                    courtNameFormat = configItemDTO.getValue();
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
        validCourtConfig(courtCount, courtNameFormat);
        if (courtCount != previousCourtCount && !canUpdateCourtCountNow(sessions, warmupMinutes)) {
            throw new BaseException(ResponseCode.COURT_COUNT_UPDATE_WINDOW_CLOSED);
        }
        if (courtCount != previousCourtCount || !Objects.equals(courtNameFormat, previousCourtNameFormat)) {
            syncCourts(courtCount, courtNameFormat);
        }

        Set<ConfigKey> changedKeys = new HashSet<>();
        for (ConfigItemDTO configItemDTO : configDTO.getConfigItems()) {
            if (configItemDTO.getConfigKey() == null || configItemDTO.getValue() == null || configItemDTO.getValue().isEmpty()) {
                continue;
            }
            Config current = configMapper.selectOne(new LambdaQueryWrapper<Config>()
                    .eq(Config::getConfigKey, configItemDTO.getConfigKey()));
            if (current == null || !Objects.equals(current.getValue(), configItemDTO.getValue())) {
                changedKeys.add(configItemDTO.getConfigKey());
            }
            upsertConfigValue(configItemDTO.getConfigKey(), configItemDTO.getValue());
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
        if (changedKeys.contains(ConfigKey.COURT_COUNT)) {
            regenerateTodaySlotsForAllSessions();
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
        FlashSession oldSession = sessionMapper.selectById(id);
        if (oldSession == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        if (flashSession.getFlashTime() == null) {
            flashSession.setFlashTime(oldSession.getFlashTime());
        }
        if (flashSession.getBeginTime() == null) {
            flashSession.setBeginTime(oldSession.getBeginTime());
        }
        if (flashSession.getEndTime() == null) {
            flashSession.setEndTime(oldSession.getEndTime());
        }
        if (flashSession.getSlotInterval() == null) {
            flashSession.setSlotInterval(oldSession.getSlotInterval());
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
    public void warmupSession(Integer sessionId) {
        FlashSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        warmupSession(session);
    }

    @Override
    public void warmupSession(FlashSession session) {
        generateSlot(session.getId());
        LocalDate today = DateTimes.nowDate();
        long ttlSec = DateTimes.ttlToEndOfTodaySeconds();
        List<TimeSlot> timeSlots = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()
                .eq(TimeSlot::getSessionId, session.getId())
                .eq(TimeSlot::getSlotDate, today));
        if (timeSlots.isEmpty()) {
            return;
        }
        RBucket<String> sessionDone = redisson.getBucket(RedisKeys.warmupSessionDoneKey(today, session.getId()));
        if (sessionDone.isExists() && isSessionWarmupComplete(session.getId(), timeSlots)) {
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
            if (sessionDone.isExists() && isSessionWarmupComplete(session.getId(), timeSlots)) {
                return;
            }
            for (TimeSlot timeSlot : timeSlots) {
                Integer slotId = timeSlot.getId();
                RBucket<String> warmFlag = redisson.getBucket(RedisKeys.warmupDoneKey(slotId));

                if (warmFlag.isExists() && isSlotWarmupComplete(slotId, session.getId())) {
                    continue;
                }
                RBucket<Integer> slotSession = redisson.getBucket(RedisKeys.slotSessionKey(slotId));
                if (!slotSession.isExists()) {
                    slotSession.set(session.getId(), Duration.ofSeconds(ttlSec));
                }
                RSemaphore semaphore = redisson.getSemaphore(RedisKeys.semKey(slotId));
                if (!semaphore.isExists()) {
                    semaphore.trySetPermits(1);
                }
                semaphore.expire(Duration.ofSeconds(ttlSec));

                RSet<Integer> dedup = redisson.getSet(RedisKeys.dedupKey(slotId));
                if (!dedup.isExists()) {
                    dedup.add(-1); // 占位，确保 key 存在
                }
                dedup.expire(Duration.ofSeconds(ttlSec));

                warmFlag.set("1", Duration.ofSeconds(ttlSec));


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
            if (isSessionWarmupComplete(session.getId(), timeSlots)) {
                sessionDone.set("1", Duration.ofSeconds(ttlSec));
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("已完成为场次 {} 预热时间槽", session.getId());
            }
        }
    }

    private boolean isSessionWarmupComplete(Integer sessionId, List<TimeSlot> timeSlots) {
        if (timeSlots == null || timeSlots.isEmpty()) {
            return false;
        }
        for (TimeSlot timeSlot : timeSlots) {
            if (!isSlotWarmupComplete(timeSlot.getId(), sessionId)) {
                return false;
            }
        }
        if (!redisson.getBucket(RedisKeys.gateKey(sessionId)).isExists()) {
            return false;
        }
        return redisson.getBucket(RedisKeys.gateTimeKey(sessionId)).isExists();
    }

    private boolean isSlotWarmupComplete(Integer slotId, Integer sessionId) {
        RBucket<Integer> slotSession = redisson.getBucket(RedisKeys.slotSessionKey(slotId));
        Integer cachedSessionId = slotSession.get();
        if (!sessionId.equals(cachedSessionId)) {
            return false;
        }
        if (!redisson.getBucket(RedisKeys.warmupDoneKey(slotId)).isExists()) {
            return false;
        }
        if (!redisson.getSemaphore(RedisKeys.semKey(slotId)).isExists()) {
            return false;
        }
        return redisson.getSet(RedisKeys.dedupKey(slotId)).isExists();
    }

    @Override
    public void openSession(Integer sessionId) {
        FlashSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        warmupSession(session);
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
                log.info("已完成为场次 {} 生成时间槽", sessionId);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void reconcileCourtsByConfigAtStartup() {
        int warmupMinutes;
        int targetCourtCount;
        String courtNameFormat;
        try {
            warmupMinutes = Integer.parseInt(getConfigValue(ConfigKey.WARMUP_MINUTE));
            targetCourtCount = getConfigIntOrDefault(ConfigKey.COURT_COUNT, countActiveCourts());
            courtNameFormat = getConfigStringOrDefault(ConfigKey.COURT_NAME_FORMAT, DEFAULT_COURT_NAME_FORMAT);
        } catch (Exception e) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        validCourtConfig(targetCourtCount, courtNameFormat);
        upsertConfigValue(ConfigKey.COURT_COUNT, String.valueOf(targetCourtCount));
        upsertConfigValue(ConfigKey.COURT_NAME_FORMAT, courtNameFormat);

        List<Court> currentCourts = getActiveCourtsById();
        boolean countMismatched = currentCourts.size() != targetCourtCount;
        boolean nameMismatched = isCourtNameMismatched(currentCourts, courtNameFormat, targetCourtCount);
        if (!countMismatched && !nameMismatched) {
            return;
        }

        List<FlashSession> sessions = getSessions();
        if (!canUpdateCourtCountNow(sessions, warmupMinutes)) {
            log.warn("启动对账发现球场配置不一致，但当前已过可变更窗口，跳过修复: targetCount={}, actualCount={}, nameMismatched={}",
                    targetCourtCount, currentCourts.size(), nameMismatched);
            return;
        }

        syncCourts(targetCourtCount, courtNameFormat);
        if (countMismatched) {
            regenerateTodaySlotsForAllSessions();
        }
        log.info("启动球场配置对账完成: targetCount={}, nameFormat={}", targetCourtCount, courtNameFormat);
    }

    private void upsertConfigValue(ConfigKey configKey, String value) {
        Config current = configMapper.selectOne(new LambdaQueryWrapper<Config>()
                .eq(Config::getConfigKey, configKey));
        if (current == null) {
            configMapper.insert(new Config(new ConfigItemDTO(configKey, value)));
            return;
        }
        configMapper.update(null, new LambdaUpdateWrapper<Config>()
                .set(Config::getValue, value)
                .eq(Config::getConfigKey, configKey));
    }

    private int getConfigIntOrDefault(ConfigKey configKey, int defaultValue) {
        String configValue = getConfigStringOrDefault(configKey, null);
        if (configValue == null) {
            return defaultValue;
        }
        return Integer.parseInt(configValue);
    }

    private String getConfigStringOrDefault(ConfigKey configKey, String defaultValue) {
        Config config = configMapper.selectOne(new LambdaQueryWrapper<Config>()
                .eq(Config::getConfigKey, configKey));
        if (config == null || config.getValue() == null || config.getValue().isEmpty()) {
            return defaultValue;
        }
        return config.getValue();
    }

    private int countActiveCourts() {
        return courtMapper.selectCount(null).intValue();
    }

    private void validCourtConfig(int courtCount, String courtNameFormat) {
        if (courtCount < 0 || courtNameFormat == null || !courtNameFormat.contains("%d")) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        Set<String> names = new HashSet<>();
        try {
            for (int i = 1; i <= courtCount; i++) {
                names.add(String.format(courtNameFormat, i));
            }
        } catch (Exception e) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        if (names.size() != courtCount) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
    }

    private boolean canUpdateCourtCountNow(List<FlashSession> sessions, int warmupMinutes) {
        if (sessions == null || sessions.isEmpty()) {
            return true;
        }
        LocalTime firstWarmup = null;
        for (FlashSession session : sessions) {
            if (session == null || session.getFlashTime() == null) {
                continue;
            }
            LocalTime warmupTime = session.getFlashTime().minusMinutes(warmupMinutes);
            if (firstWarmup == null || warmupTime.isBefore(firstWarmup)) {
                firstWarmup = warmupTime;
            }
        }
        if (firstWarmup == null) {
            return true;
        }
        return DateTimes.nowTime().isBefore(firstWarmup);
    }

    private void syncCourts(int targetCount, String courtNameFormat) {
        List<Court> activeCourts = getActiveCourtsById();
        int currentCount = activeCourts.size();
        if (currentCount < targetCount) {
            for (int i = currentCount + 1; i <= targetCount; i++) {
                Court court = new Court();
                court.setCourtName(String.format(courtNameFormat, i));
                courtMapper.insert(court);
            }
            activeCourts = getActiveCourtsById();
        } else if (currentCount > targetCount) {
            for (int i = targetCount; i < currentCount; i++) {
                courtMapper.deleteById(activeCourts.get(i).getId());
            }
            activeCourts = getActiveCourtsById();
        }
        for (int i = 0; i < activeCourts.size(); i++) {
            Court court = activeCourts.get(i);
            String targetName = String.format(courtNameFormat, i + 1);
            if (Objects.equals(targetName, court.getCourtName())) {
                continue;
            }
            courtMapper.update(null, new LambdaUpdateWrapper<Court>()
                    .set(Court::getCourtName, targetName)
                    .eq(Court::getId, court.getId()));
        }
    }

    private List<Court> getActiveCourtsById() {
        List<Court> courts = courtMapper.selectList(new LambdaQueryWrapper<Court>().orderByAsc(Court::getId));
        return courts == null ? new ArrayList<>() : courts;
    }

    private void regenerateTodaySlotsForAllSessions() {
        List<FlashSession> sessions = getSessions();
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        LocalDate today = DateTimes.nowDate();
        for (FlashSession session : sessions) {
            regenerateTodaySlotsForSession(today, session.getId());
        }
    }

    private void regenerateTodaySlotsForSession(LocalDate day, Integer sessionId) {
        RLock lock = redisson.getLock(RedisKeys.slotGenLockKey(day, sessionId));
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
            List<TimeSlot> oldSlots = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()
                    .eq(TimeSlot::getSessionId, sessionId)
                    .eq(TimeSlot::getSlotDate, day));
            timeSlotMapper.delete(new LambdaQueryWrapper<TimeSlot>()
                    .eq(TimeSlot::getSessionId, sessionId)
                    .eq(TimeSlot::getSlotDate, day));
            cleanupReserveRedisBySlots(oldSlots);
            redisson.getBucket(RedisKeys.slotGenDoneKey(day, sessionId)).delete();
            redisson.getBucket(RedisKeys.warmupSessionDoneKey(day, sessionId)).delete();
            redisson.getBucket(RedisKeys.gateKey(sessionId)).delete();
            redisson.getBucket(RedisKeys.gateTimeKey(sessionId)).delete();
            timeSlotService.generateForDate(day, sessionId);
            long ttl = Math.max(DateTimes.ttlToEndOfDaySeconds(day), 60L);
            redisson.getBucket(RedisKeys.slotGenDoneKey(day, sessionId)).set("1", Duration.ofSeconds(ttl));
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void cleanupReserveRedisBySlots(List<TimeSlot> slots) {
        if (slots == null || slots.isEmpty()) {
            return;
        }
        for (TimeSlot slot : slots) {
            if (slot == null || slot.getId() == null) {
                continue;
            }
            Integer slotId = slot.getId();
            redisson.getBucket(RedisKeys.slotSessionKey(slotId)).delete();
            redisson.getSemaphore(RedisKeys.semKey(slotId)).delete();
            redisson.getSet(RedisKeys.dedupKey(slotId)).delete();
            redisson.getBucket(RedisKeys.warmupDoneKey(slotId)).delete();
        }
    }

    private boolean isCourtNameMismatched(List<Court> courts, String courtNameFormat, int courtCount) {
        int max = Math.min(courts.size(), courtCount);
        for (int i = 0; i < max; i++) {
            String expectedName = String.format(courtNameFormat, i + 1);
            if (!Objects.equals(expectedName, courts.get(i).getCourtName())) {
                return true;
            }
        }
        return false;
    }
}
