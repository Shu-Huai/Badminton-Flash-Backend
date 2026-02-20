package shuhuai.badmintonflashbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.redisson.api.RBucket;
import org.redisson.api.RSemaphore;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import shuhuai.badmintonflashbackend.constant.RedisKeys;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.entity.Config;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.entity.TimeSlot;
import shuhuai.badmintonflashbackend.mapper.ConfigMapper;
import shuhuai.badmintonflashbackend.mapper.IFlashSessionMapper;
import shuhuai.badmintonflashbackend.mapper.ITimeSlotMapper;
import shuhuai.badmintonflashbackend.model.dto.ConfigDTO;
import shuhuai.badmintonflashbackend.model.dto.ConfigItemDTO;
import shuhuai.badmintonflashbackend.model.dto.FlashSessionDTO;
import shuhuai.badmintonflashbackend.service.AdminService;
import shuhuai.badmintonflashbackend.service.ITimeSlotService;
import shuhuai.badmintonflashbackend.utils.DateTimes;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

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
    public void updateConfig(ConfigItemDTO configItemDTO) {
        if (configItemDTO.getValue() == null) {
            return;
        }
        configMapper.update(null, new LambdaUpdateWrapper<Config>()
                .set(Config::getValue, configItemDTO.getValue())
                .eq(Config::getConfigKey, configItemDTO.getConfigKey()));
    }

    @Override
    public void updateConfig(ConfigDTO configDTO) {
        for (ConfigItemDTO configItemDTO : configDTO.getConfigItems()) {
            updateConfig(configItemDTO);
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
        sessionMapper.insert(flashSession);
        timeSlotService.addSessionHandler(flashSession.getId());
    }

    @Override
    public void updateSession(Integer id, FlashSessionDTO flashSessionDTO) {
        FlashSession flashSession = new FlashSession(flashSessionDTO);
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
        long ttlSec = DateTimes.ttlToEndOfTodaySeconds();
        List<TimeSlot> timeSlots = timeSlotMapper.selectList(new LambdaQueryWrapper<TimeSlot>()
                .eq(TimeSlot::getSessionId, session.getId()));
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
            RSet<Long> dedup = redisson.getSet(RedisKeys.dedupKey(slotId));
            dedup.delete();                          // 清空
            dedup.add(-1L);                          // 确保 key 存在
            dedup.expire(Duration.ofSeconds(ttlSec));
        }
        RBucket<String> gate = redisson.getBucket(RedisKeys.gateKey(session.getId()));
        gate.set("0", Duration.ofSeconds(ttlSec));

        long startEpoch = ZonedDateTime
                .of(LocalDate.now(DateTimes.zone()), session.getFlashTime(), DateTimes.zone())
                .toEpochSecond();
        RBucket<Long> gateTime = redisson.getBucket(RedisKeys.gateTimeKey(session.getId()));
        gateTime.set(startEpoch, Duration.ofSeconds(ttlSec));
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
        timeSlotService.generateForDate(LocalDate.now(DateTimes.zone()), sessionId);
    }
}
