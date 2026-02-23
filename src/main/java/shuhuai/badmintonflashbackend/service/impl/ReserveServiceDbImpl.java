package shuhuai.badmintonflashbackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shuhuai.badmintonflashbackend.enm.PayOrderStatus;
import shuhuai.badmintonflashbackend.enm.ReservationStatus;
import shuhuai.badmintonflashbackend.enm.ReserveResultStatus;
import shuhuai.badmintonflashbackend.entity.FlashSession;
import shuhuai.badmintonflashbackend.entity.PayOrder;
import shuhuai.badmintonflashbackend.entity.Reservation;
import shuhuai.badmintonflashbackend.entity.TimeSlot;
import shuhuai.badmintonflashbackend.excep.BaseException;
import shuhuai.badmintonflashbackend.mapper.IFlashSessionMapper;
import shuhuai.badmintonflashbackend.mapper.IPayOrderMapper;
import shuhuai.badmintonflashbackend.mapper.IReservationMapper;
import shuhuai.badmintonflashbackend.mapper.ITimeSlotMapper;
import shuhuai.badmintonflashbackend.model.vo.ReserveResultVO;
import shuhuai.badmintonflashbackend.response.ResponseCode;
import shuhuai.badmintonflashbackend.service.IReserveService;
import shuhuai.badmintonflashbackend.utils.DateTimes;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "reserve.mode", havingValue = "db")
public class ReserveServiceDbImpl implements IReserveService {
    private final ITimeSlotMapper timeSlotMapper;
    private final IFlashSessionMapper flashSessionMapper;
    private final IReservationMapper reservationMapper;
    private final IPayOrderMapper payOrderMapper;

    public ReserveServiceDbImpl(ITimeSlotMapper timeSlotMapper, IFlashSessionMapper flashSessionMapper,
                                IReservationMapper reservationMapper, IPayOrderMapper payOrderMapper) {
        this.timeSlotMapper = timeSlotMapper;
        this.flashSessionMapper = flashSessionMapper;
        this.reservationMapper = reservationMapper;
        this.payOrderMapper = payOrderMapper;
    }

    @Override
    public String reserve(Integer userId, Integer slotId, Integer sessionId) {
        if (userId == null || slotId == null || sessionId == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }

        TimeSlot timeSlot = timeSlotMapper.selectOne(new LambdaQueryWrapper<TimeSlot>()
                .eq(TimeSlot::getId, slotId)
                .eq(TimeSlot::getSessionId, sessionId));
        if (timeSlot == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }

        FlashSession flashSession = flashSessionMapper.selectById(sessionId);
        if (flashSession == null || flashSession.getFlashTime() == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }

        if (timeSlot.getSlotDate() == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        if (timeSlot.getSlotDate().isAfter(DateTimes.nowDate())) {
            throw new BaseException(ResponseCode.UNGATED);
        }
        if (DateTimes.nowDate().equals(timeSlot.getSlotDate())
                && DateTimes.nowTime().isBefore(flashSession.getFlashTime())) {
            throw new BaseException(ResponseCode.UNGATED);
        }

        String traceId = UUID.randomUUID().toString();
        Reservation reservation = new Reservation();
        reservation.setUserId(userId);
        reservation.setSlotId(slotId);
        reservation.setTraceId(traceId);
        reservation.setStatus(ReservationStatus.PENDING_PAYMENT);

        try {
            reservationMapper.insert(reservation);
            return traceId;
        } catch (DuplicateKeyException e) {
            Reservation existed = reservationMapper.selectOne(new LambdaQueryWrapper<Reservation>()
                    .eq(Reservation::getSlotId, slotId)
                    .in(Reservation::getStatus, ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED));
            if (existed != null && userId.equals(existed.getUserId())) {
                throw new BaseException(ResponseCode.DUP_REQ);
            }
            if (existed != null) {
                throw new BaseException(ResponseCode.OUT_OF_STOCK);
            }
            throw new BaseException(ResponseCode.FAILED);
        }
    }

    @Override
    public ReserveResultVO getReserveResult(Integer userId, String traceId) {
        if (userId == null || traceId == null || traceId.isBlank()) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        Reservation reservation = reservationMapper.selectOne(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getTraceId, traceId));
        if (reservation == null) {
            return new ReserveResultVO(traceId, ReserveResultStatus.FAILED, null, null);
        }
        if (!userId.equals(reservation.getUserId())) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        return new ReserveResultVO(
                traceId,
                ReserveResultStatus.SUCCESS,
                reservation.getId(),
                reservation.getStatus()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Integer userId, Integer reservationId) {
        if (userId == null || reservationId == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        Reservation reservation = reservationMapper.selectOne(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getId, reservationId)
                .eq(Reservation::getUserId, userId));
        if (reservation == null) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        if (reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
        int updated = reservationMapper.update(null, new LambdaUpdateWrapper<Reservation>()
                .set(Reservation::getStatus, ReservationStatus.CANCELLED)
                .eq(Reservation::getId, reservationId)
                .eq(Reservation::getUserId, userId)
                .eq(Reservation::getStatus, ReservationStatus.PENDING_PAYMENT));
        if (updated <= 0) {
            throw new BaseException(ResponseCode.FAILED);
        }
        closePayingOrders(reservationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTimeoutPending(Integer reservationId) {
        if (reservationId == null) {
            return;
        }
        Reservation reservation = reservationMapper.selectById(reservationId);
        if (reservation == null || reservation.getStatus() != ReservationStatus.PENDING_PAYMENT) {
            return;
        }
        int updated = reservationMapper.update(null, new LambdaUpdateWrapper<Reservation>()
                .set(Reservation::getStatus, ReservationStatus.CANCELLED)
                .eq(Reservation::getId, reservationId)
                .eq(Reservation::getStatus, ReservationStatus.PENDING_PAYMENT));
        if (updated <= 0) {
            return;
        }
        closePayingOrders(reservationId);
    }

    private void closePayingOrders(Integer reservationId) {
        if (reservationId == null) {
            return;
        }
        payOrderMapper.update(null, new LambdaUpdateWrapper<PayOrder>()
                .set(PayOrder::getStatus, PayOrderStatus.CLOSED)
                .eq(PayOrder::getReservationId, reservationId)
                .eq(PayOrder::getStatus, PayOrderStatus.PAYING));
    }
}
