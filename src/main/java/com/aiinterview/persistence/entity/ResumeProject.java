package com.aiinterview.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("resume_project")
public class ResumeProject {
    @TableId(type = IdType.INPUT)
    public String id;
    public String resumeId;
    public String versionId;
    public String name;
    public String goal;
    public String responsibility;
    public String techStack;
    public String difficulty;
    public String solution;
    public String result;
    public String improvement;
    public Integer sortNo;
}

