package com.aiinterview.auth;

import com.aiinterview.common.Values;
import com.aiinterview.persistence.entity.User;
import com.aiinterview.persistence.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.boot.*;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final Environment env;
    private final UserMapper users;
    private final PasswordEncoder encoder;
    public AdminBootstrap(Environment env,UserMapper users,PasswordEncoder encoder) { this.env=env; this.users=users; this.encoder=encoder; }
    @Override @Transactional public void run(ApplicationArguments args) {
        if (!args.containsOption("bootstrap-admin")) return;
        String account=env.getProperty("BOOTSTRAP_ADMIN_ACCOUNT");
        String password=env.getProperty("BOOTSTRAP_ADMIN_PASSWORD");
        if(account==null || !account.matches("[A-Za-z0-9_]{8,16}") || password==null || password.length()<8 || !password.matches("(?s)(?=.*[A-Za-z])(?=.*[0-9]).+"))
            throw new IllegalArgumentException("Set valid BOOTSTRAP_ADMIN_ACCOUNT and BOOTSTRAP_ADMIN_PASSWORD in the process environment");
        AuthService.validatePasswordBytes(password);
        account=account.toLowerCase(java.util.Locale.ROOT);
        if(users.selectCount(new QueryWrapper<User>().eq("account",account))>0)
            throw new IllegalArgumentException("Bootstrap account already exists; existing accounts are never promoted");
        User user=new User(); user.id=Values.id(); user.account=account; user.passwordHash=encoder.encode(password);
        user.nickname="管理员"; user.role="ADMIN"; user.status="ACTIVE"; user.createdAt=Values.now(); user.updatedAt=user.createdAt; user.updatedBy=user.id;
        users.insert(user);
    }
}
