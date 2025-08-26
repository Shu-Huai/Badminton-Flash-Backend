package shuhuai.badmintonflashbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.entity.Config;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.mapper.ConfigMapper;
import shuhuai.badmintonflashbackend.mapper.IFlashSessionMapper;
import shuhuai.badmintonflashbackend.model.dto.ConfigDTO;
import shuhuai.badmintonflashbackend.model.dto.ConfigItemDTO;
import shuhuai.badmintonflashbackend.model.dto.FlashSessionDTO;
import shuhuai.badmintonflashbackend.service.AdminService;
import shuhuai.badmintonflashbackend.service.ITimeSlotService;

import java.util.List;

@Service
public class AdminServiceImpl implements AdminService {
    private final ConfigMapper configMapper;
    private final IFlashSessionMapper sessionMapper;
    private final ITimeSlotService timeSlotService;

    @Autowired
    public AdminServiceImpl(ConfigMapper configMapper, IFlashSessionMapper sessionMapper, ITimeSlotService timeSlotService) {
        this.configMapper = configMapper;
        this.sessionMapper = sessionMapper;
        this.timeSlotService = timeSlotService;
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
}
