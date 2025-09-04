package shuhuai.badmintonflashbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import shuhuai.badmintonflashbackend.entity.Reservation;

@Mapper
public interface IReservationMapper extends BaseMapper<Reservation> {
}