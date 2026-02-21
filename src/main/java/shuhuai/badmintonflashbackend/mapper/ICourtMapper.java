package shuhuai.badmintonflashbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import shuhuai.badmintonflashbackend.entity.Court;

@Mapper
public interface ICourtMapper extends BaseMapper<Court> {
}
