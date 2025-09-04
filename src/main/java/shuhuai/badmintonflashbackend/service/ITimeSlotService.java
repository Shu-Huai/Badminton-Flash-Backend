package shuhuai.badmintonflashbackend.service;

import java.time.LocalDate;

public interface ITimeSlotService {
    void generateForDate(LocalDate date);

    void generateForDate(LocalDate date, Integer sessionId);

    void addSessionHandler(Integer sessionId);

    void updateSessionHandler(Integer sessionId);

    void deleteSessionHandler(Integer sessionId);
}