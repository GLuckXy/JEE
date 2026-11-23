package com.aiinterview.user.service;

import com.aiinterview.auth.service.UserAccessService;
import com.aiinterview.common.BusinessException;
import com.aiinterview.common.PageResult;
import com.aiinterview.common.Values;
import com.aiinterview.user.dto.UserRequests;
import com.aiinterview.user.dto.UserView;
import com.aiinterview.user.persistence.User;
import com.aiinterview.user.persistence.UserAuditLog;
import com.aiinterview.user.persistence.UserAuditLogMapper;
import com.aiinterview.user.persistence.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserMapper users;
    private final UserAuditLogMapper audit;
    private final UserAccessService access;
    public UserService(UserMapper users, UserAuditLogMapper audit, UserAccessService access) { this.users=users; this.audit=audit; this.access=access; }
    public UserView me() { return UserView.of(find(access.getCurrentUser().id())); }
    @Transactional public UserView updateMe(UserRequests.Profile p) {
        String id=access.getCurrentUser().id();
        users.update(null,new UpdateWrapper<User>().eq("id",id).set("nickname",p.nickname()).set("email",p.email())
                .set("phone",p.phone()).set("school",p.school()).set("major",p.major()).set("updated_by",id).set("updated_at",Values.now()));
        return UserView.of(find(id));
    }
    @PreAuthorize("hasRole('ADMIN')")
    public PageResult<UserView> list(int page,int size) {
        int offset=PageResult.offset(page,size);
        var records=users.selectList(new QueryWrapper<User>().orderByDesc("created_at").orderByAsc("id").last("LIMIT "+size+" OFFSET "+offset));
        return new PageResult<>(users.selectCount(null),page,size,records.stream().map(UserView::of).toList());
    }
    @PreAuthorize("hasRole('ADMIN')") public UserView detail(String id) { return UserView.of(find(id)); }
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public UserView setStatus(String id,UserRequests.Status p) {
        User target=find(id);
        // Initial admin management scope is student accounts only.
        if (!"STUDENT".equals(target.role)) throw BusinessException.bad("ADMIN_OPERATION_FORBIDDEN","初版不允许修改管理员状态");
        change(id,"status",p.status(),"UPDATE_STATUS_"+p.status()); return UserView.of(find(id));
    }
    @PreAuthorize("hasRole('ADMIN')") @Transactional
    public UserView adminUpdate(String id,UserRequests.AdminProfile p) {
        find(id); change(id,"nickname",p.nickname(),"UPDATE_NICKNAME"); return UserView.of(find(id));
    }
    private void change(String id,String field,String value,String action) {
        String actor=access.getCurrentUser().id();
        users.update(null,new UpdateWrapper<User>().eq("id",id).set(field,value).set("updated_by",actor).set("updated_at",Values.now()));
        UserAuditLog entry=new UserAuditLog(); entry.id=Values.id(); entry.actorId=actor; entry.targetId=id; entry.action=action; entry.createdAt=Values.now(); audit.insert(entry);
    }
    private User find(String id) { User user=users.selectById(id); if(user==null) throw BusinessException.missing("USER_NOT_FOUND"); return user; }
}
