package com.aiinterview.logging.service;

import com.aiinterview.common.PageResult;
import com.aiinterview.common.Values;
import com.aiinterview.logging.dto.OperationLogView;
import com.aiinterview.logging.persistence.OperationLog;
import com.aiinterview.logging.persistence.OperationLogMapper;
import com.aiinterview.user.persistence.User;
import com.aiinterview.user.persistence.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class OperationLogService {
    private final OperationLogMapper logs;
    private final UserMapper users;
    public OperationLogService(OperationLogMapper logs, UserMapper users) { this.logs=logs; this.users=users; }
    public void record(String userId, String action, String targetId, String detail) {
        OperationLog entry=new OperationLog();
        entry.id=Values.id(); entry.userId=userId; entry.action=action;
        entry.targetId=targetId; entry.detail=detail; entry.createdAt=Values.now();
        logs.insert(entry);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PageResult<OperationLogView> list(int page, int size, String userId, String action) {
        int offset=PageResult.offset(page,size);
        QueryWrapper<OperationLog> filter=new QueryWrapper<>();
        if (userId!=null && !userId.isBlank()) filter.eq("user_id",userId);
        if (action!=null && !action.isBlank()) filter.eq("action",action);
        long total=logs.selectCount(filter);
        var records=logs.selectList(filter.orderByDesc("created_at").orderByDesc("id").last("LIMIT "+size+" OFFSET "+offset));
        Map<String,User> actors=users.selectBatchIds(records.stream().map(row->row.userId).distinct().toList())
                .stream().collect(Collectors.toMap(u->u.id,Function.identity()));
        List<OperationLogView> views=records.stream().map(row -> {
            User actor=actors.get(row.userId);
            return new OperationLogView(row.id,row.userId,actor==null?null:actor.account,row.action,row.targetId,row.detail,row.createdAt);
        }).toList();
        return new PageResult<>(total,page,size,views);
    }
}
