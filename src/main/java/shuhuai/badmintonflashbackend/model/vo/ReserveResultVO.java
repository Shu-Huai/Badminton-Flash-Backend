package shuhuai.badmintonflashbackend.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import shuhuai.badmintonflashbackend.enm.ReservationStatus;
import shuhuai.badmintonflashbackend.enm.ReserveResultStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReserveResultVO {
    private String traceId;
    private ReserveResultStatus status;
    private Integer reservationId;
    private ReservationStatus reservationStatus;
}
