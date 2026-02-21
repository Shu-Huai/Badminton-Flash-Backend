package shuhuai.badmintonflashbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import shuhuai.badmintonflashbackend.excep.BaseException;
import shuhuai.badmintonflashbackend.model.dto.FlashSessionDTO;
import shuhuai.badmintonflashbackend.response.ResponseCode;

import java.time.LocalDateTime;
import java.time.LocalTime;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class FlashSession {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private LocalTime flashTime;
    private LocalTime beginTime;
    private LocalTime endTime;
    private Integer slotInterval;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic(value = "1", delval = "0")
    private Integer isActive;

    public FlashSession(FlashSessionDTO flashSessionDTO) {
        try {
            this.flashTime = LocalTime.parse(flashSessionDTO.getFlashTime());
            this.beginTime = LocalTime.parse(flashSessionDTO.getBeginTime());
            this.endTime = LocalTime.parse(flashSessionDTO.getEndTime());
            this.slotInterval = flashSessionDTO.getSlotInterval();
        } catch (Exception e) {
            throw new BaseException(ResponseCode.PARAM_ERROR);
        }
    }
}