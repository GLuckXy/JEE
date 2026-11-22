package com.example.interview.training;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
interface AnswerMapper extends BaseMapper<TrainingRows.Answer> {
    @Select("SELECT * FROM training_answer WHERE id = #{id} FOR UPDATE")
    TrainingRows.Answer lock(String id);
}
