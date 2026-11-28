package com.aiinterview.logging.controller;

import com.aiinterview.common.ApiResponse;
import com.aiinterview.common.PageResult;
import com.aiinterview.logging.dto.OperationLogView;
import com.aiinterview.logging.service.OperationLogService;
import org.springframework.web.bind.annotation.*;

@RestController
public class OperationLogController {
    private final OperationLogService logs;
    public OperationLogController(OperationLogService logs) { this.logs=logs; }
    @GetMapping("/api/admin/logs")
    public ApiResponse<PageResult<OperationLogView>> list(@RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String userId,
            @RequestParam(required=false) String action) {
        return ApiResponse.ok(logs.list(page,size,userId,action));
    }
}
