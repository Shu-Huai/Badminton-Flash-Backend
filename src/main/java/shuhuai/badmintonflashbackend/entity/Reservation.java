package shuhuai.badmintonflashbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import shuhuai.badmintonflashbackend.enm.ReservationStatus;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Reservation {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer userId;
    private Integer slotId;
    private ReservationStatus status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "1", delval = "0")
    private Boolean isActive;
}
