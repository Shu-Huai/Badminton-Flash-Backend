package shuhuai.badmintonflashbackend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
public class ConditionBrowseSessionDTO {
    private LocalTime flashTimeLowerBound;
    private LocalTime flashTimeUpperBound;
    private LocalTime beginTimeLowerBound;
    private LocalTime beginTimeUpperBound;
    private LocalTime endTimeLowerBound;
    private LocalTime endTimeUpperBound;
    private Integer slotIntervalLowerBound;
    private Integer slotIntervalUpperBound;
}
