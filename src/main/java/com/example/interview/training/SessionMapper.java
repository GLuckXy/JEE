package com.example.interview.training;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
interface SessionMapper extends BaseMapper<TrainingRows.Session> {
    @Select("SELECT * FROM training_session WHERE id = #{id} AND owner_id = #{ownerId} FOR UPDATE")
    TrainingRows.Session lock(String id, String ownerId);
}
