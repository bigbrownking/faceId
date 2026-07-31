package org.ktz.faceid.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * FaceAuth trusts the orchestrator (it's the only caller, bound to 127.0.0.1).
 * The orchestrator passes the authenticated user's id in X-User-Id.
 * We expose it as request attribute "userId" for controllers.
 */
@Component
@Order(1)
public class UserIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        String uid = http.getHeader("X-User-Id");
        if (uid != null && !uid.isBlank()) {
            try {
                http.setAttribute("userId", Long.parseLong(uid));
            } catch (NumberFormatException ignored) {}
        }
        chain.doFilter(req, res);
    }
}