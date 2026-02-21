package shuhuai.badmintonflashbackend.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReserveMessage implements Serializable {
    private Integer userId;
    private Integer slotId;
    private Integer sessionId;
    private String traceId;
}
