package shuhuai.badmintonflashbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import shuhuai.badmintonflashbackend.entity.Config;

@Mapper
public interface IConfigMapper extends BaseMapper<Config> {
}