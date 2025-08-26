package shuhuai.badmintonflashbackend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class FlashSessionDTO {
    private String flashTime;
    private String beginTime;
    private String endTime;
    private Integer slotInterval;
}
