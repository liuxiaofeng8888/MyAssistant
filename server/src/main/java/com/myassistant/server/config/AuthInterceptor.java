package com.myassistant.server.config;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {
  private final MyAssistantProperties props;

  public AuthInterceptor(MyAssistantProperties props) {
    this.props = props;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    if (!props.getAuth().isEnabled()) {
      return true;
    }
    String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
    String expected = "Bearer " + props.getAuth().getStaticBearerToken();
    if (expected.equals(auth)) {
      return true;
    }
    response.setStatus(401);
    response.setContentType("application/json;charset=utf-8");
    response.getWriter().write("{\"ok\":false,\"code\":\"UNAUTHORIZED\"}");
    return false;
  }
}

