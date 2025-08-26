package shuhuai.badmintonflashbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlot {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer courtId;
    private Integer sessionId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "1", delval = "0")
    private Boolean isActive;
}
