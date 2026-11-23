package com.aiinterview.voice.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VoiceAssetMapper extends BaseMapper<VoiceAssetMapper.Asset> {
    @TableName("voice_asset")
    class Asset {
        @TableId(type = IdType.INPUT) public String id;
        public String ownerId;
        public String mediaType;
        public String fileExtension;
        public long byteCount;
    }
}
