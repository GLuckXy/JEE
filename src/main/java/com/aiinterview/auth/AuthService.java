package com.aiinterview.auth;

import com.aiinterview.common.*;
import com.aiinterview.config.AppProperties;
import com.aiinterview.persistence.entity.*;
import com.aiinterview.persistence.mapper.*;
import com.aiinterview.user.UserView;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.*;

@Service
public class AuthService {
    private final UserMapper users;
    private final AuthSessionMapper sessions;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final AppProperties properties;
    private final String dummyHash;
    public AuthService(UserMapper users, AuthSessionMapper sessions, PasswordEncoder passwords, JwtEncoder encoder, AppProperties properties) {
        this.users=users; this.sessions=sessions; this.passwords=passwords; this.encoder=encoder; this.properties=properties;
        this.dummyHash=passwords.encode(Values.id());
    }
    public static void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw BusinessException.bad("VALIDATION_FAILED", "密码 UTF-8 长度不得超过 72 字节");
    }
    @Transactional
    public UserView register(AuthRequests.Register request) {
        validatePasswordBytes(request.password());
        User user = new User();
        user.id=Values.id(); user.account=request.account().toLowerCase(java.util.Locale.ROOT); user.nickname=request.nickname();
        user.passwordHash=passwords.encode(request.password()); user.role="STUDENT"; user.status="ACTIVE";
        user.createdAt=Values.now(); user.updatedAt=user.createdAt; user.updatedBy=user.id;
        try { users.insert(user); } catch (DuplicateKeyException e) {
            throw BusinessException.conflict("ACCOUNT_ALREADY_EXISTS", "账号已存在");
        }
        return UserView.of(user);
    }
    @Transactional
    public TokenResult login(AuthRequests.Login request) {
        validatePasswordBytes(request.password());
        User user = users.selectOne(new QueryWrapper<User>().eq("account", request.account().toLowerCase(java.util.Locale.ROOT)));
        boolean valid = passwords.matches(request.password(), user == null ? dummyHash : user.passwordHash);
        if (user == null || !valid) throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码错误");
        if (!"ACTIVE".equals(user.status)) throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "账号已禁用");
        Instant now=Instant.now(); Instant expires=now.plus(properties.tokenTtl());
        AuthSession session=new AuthSession(); session.id=Values.id(); session.userId=user.id;
        session.createdAt=LocalDateTime.ofInstant(now, ZoneOffset.UTC); session.expiresAt=LocalDateTime.ofInstant(expires,ZoneOffset.UTC);
        sessions.insert(session);
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer("ai-interview").subject(user.id).id(session.id).issuedAt(now).expiresAt(expires).build();
        String token=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResult(token, "Bearer", properties.tokenTtl().toSeconds(), UserView.of(user));
    }
    @Transactional
    public void logout(CurrentUser user) {
        sessions.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<AuthSession>()
                .eq("id", user.sessionId()).eq("user_id", user.id()).set("revoked_at", Values.now()));
    }
    public record TokenResult(String accessToken, String tokenType, long expiresIn, UserView user) {}
}
