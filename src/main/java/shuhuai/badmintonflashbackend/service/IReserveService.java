package shuhuai.badmintonflashbackend.service;

import shuhuai.badmintonflashbackend.model.vo.ReserveResultVO;

public interface IReserveService {
    String reserve(Integer userId, Integer slotId, Integer sessionId);

    ReserveResultVO getReserveResult(Integer userId, String traceId);

    void cancel(Integer userId, Integer reservationId);

    void cancelTimeoutPending(Integer reservationId);
}
