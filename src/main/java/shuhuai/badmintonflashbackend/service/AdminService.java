package shuhuai.badmintonflashbackend.service;

import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.model.dto.ConfigDTO;
import shuhuai.badmintonflashbackend.model.dto.ConfigItemDTO;
import shuhuai.badmintonflashbackend.model.dto.FlashSessionDTO;

import java.util.List;

public interface AdminService {
    void updateConfig(ConfigItemDTO configItemDTO);

    void updateConfig(ConfigDTO configDTO);

    ConfigDTO getConfig();

    String getConfigValue(ConfigKey configKey);

    void addSession(FlashSessionDTO flashSessionDTO);

    void updateSession(Integer id, FlashSessionDTO flashSessionDTO);

    void deleteSession(Integer id);

    List<FlashSession> getSessions();

    FlashSession getSession(Integer id);

    void warmupSession(Integer sessionId);

    void warmupSession(FlashSession session);

    void openSession(Integer sessionId);

    void generateSlot(Integer sessionId);
}