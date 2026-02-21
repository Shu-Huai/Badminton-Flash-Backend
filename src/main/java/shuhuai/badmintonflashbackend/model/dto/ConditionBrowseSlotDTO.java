package shuhuai.badmintonflashbackend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

@AllArgsConstructor
@Getter
@NoArgsConstructor
@Setter
public class ConditionBrowseSlotDTO {
    private Integer sessionId;
    private LocalDate dateLowerBound;
    private LocalDate dateUpperBound;
    private Set<Integer> courtIds;
    private LocalTime startTimeLowerBound;
    private LocalTime startTimeUpperBound;
    private LocalTime endTimeLowerBound;
    private LocalTime endTimeUpperBound;
}
