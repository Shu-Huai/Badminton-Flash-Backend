package shuhuai.badmintonflashbackend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReserveDTO {
    private Integer userId;
    private Integer slotId;
    private Integer sessionId;
}
