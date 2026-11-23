package com.aiinterview.config;

import com.aiinterview.auth.model.CurrentUser;
import com.aiinterview.auth.persistence.AuthSession;
import com.aiinterview.auth.persistence.AuthSessionMapper;
import com.aiinterview.common.ApiResponse;
import com.aiinterview.common.Values;
import com.aiinterview.user.persistence.User;
import com.aiinterview.user.persistence.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtDecoder decoder;
    private final UserMapper users;
    private final AuthSessionMapper sessions;
    private final ObjectMapper json;
    public JwtAuthenticationFilter(JwtDecoder decoder, UserMapper users, AuthSessionMapper sessions, ObjectMapper json) {
        this.decoder=decoder; this.users=users; this.sessions=sessions; this.json=json;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header=request.getHeader("Authorization");
        if (header == null) { chain.doFilter(request,response); return; }
        String code="AUTH_REQUIRED"; String message="令牌无效或已过期"; int status=401;
        try {
            if (!header.startsWith("Bearer ")) throw new BadJwtException("Invalid scheme");
            Jwt jwt=decoder.decode(header.substring(7));
            if (jwt.getId()==null || jwt.getSubject()==null) throw new BadJwtException("Missing identity");
            AuthSession session=sessions.selectById(jwt.getId());
            User user=users.selectById(jwt.getSubject());
            if (session == null || user == null || !user.id.equals(session.userId) || session.revokedAt != null || !session.expiresAt.isAfter(Values.now()))
                throw new BadJwtException("Revoked session");
            if (!"ACTIVE".equals(user.status)) { code="ACCOUNT_DISABLED"; message="账号已禁用"; status=403; throw new BadJwtException("Disabled"); }
            var current=new CurrentUser(user.id,user.account,user.role,session.id);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(current,null,List.of(new SimpleGrantedAuthority("ROLE_"+user.role))));
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
            json.writeValue(response.getWriter(), ApiResponse.error(code,message)); return;
        }
        chain.doFilter(request,response);
    }
}
