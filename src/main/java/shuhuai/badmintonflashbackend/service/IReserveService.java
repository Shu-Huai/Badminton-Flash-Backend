package shuhuai.badmintonflashbackend.service;

public interface IReserveService {
    void reserve(Integer userId, Integer slotId, Integer sessionId);

    void cancel(Integer userId, Integer reservationId);

    void cancelTimeoutPending(Integer reservationId);
}
