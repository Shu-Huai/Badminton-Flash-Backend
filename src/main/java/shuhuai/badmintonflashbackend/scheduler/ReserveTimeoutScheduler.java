package shuhuai.badmintonflashbackend.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import shuhuai.badmintonflashbackend.enm.ConfigKey;
import shuhuai.badmintonflashbackend.enm.ReservationStatus;
import shuhuai.badmintonflashbackend.entity.Reservation;
import shuhuai.badmintonflashbackend.mapper.IReservationMapper;
import shuhuai.badmintonflashbackend.service.IAdminService;
import shuhuai.badmintonflashbackend.service.IReserveService;
import shuhuai.badmintonflashbackend.utils.DateTimes;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ReserveTimeoutScheduler {
    private final IAdminService adminService;
    private final IReservationMapper reservationMapper;
    private final IReserveService reserveService;

    public ReserveTimeoutScheduler(IAdminService adminService, IReservationMapper reservationMapper,
                                   IReserveService reserveService) {
        this.adminService = adminService;
        this.reservationMapper = reservationMapper;
        this.reserveService = reserveService;
    }

    @Scheduled(cron = "0 * * * * ?", zone = "${app.timezone}")
    public void cancelTimeoutPendingReservation() {
        int payTimeoutMinute = Integer.parseInt(adminService.getConfigValue(ConfigKey.PAY_TIMEOUT_MINUTE));
        if (payTimeoutMinute <= 0) {
            return;
        }
        LocalDateTime deadline = DateTimes.now().minusMinutes(payTimeoutMinute);
        List<Reservation> timeoutPendingReservations = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStatus, ReservationStatus.PENDING_PAYMENT)
                .le(Reservation::getCreateTime, deadline));
        for (Reservation reservation : timeoutPendingReservations) {
            reserveService.cancelTimeoutPending(reservation.getId());
        }
    }
}
