package shuhuai.badmintonflashbackend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.utils.DateTimes;

import java.time.ZoneId;
import java.util.TimeZone;

@Component
public class TimeZoneInitializer {
    private final TimeZoneConfig timeZoneConfig;

    public TimeZoneInitializer(TimeZoneConfig timeZoneConfig) {
        this.timeZoneConfig = timeZoneConfig;
    }

    @PostConstruct
    public void init() {
        ZoneId zoneId = ZoneId.of(timeZoneConfig.getTimezone());
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        DateTimes.setAppZone(zoneId);
    }
}
