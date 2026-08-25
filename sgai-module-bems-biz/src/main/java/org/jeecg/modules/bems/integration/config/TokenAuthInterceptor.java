package org.jeecg.modules.bems.integration.config;

import com.alibaba.fastjson.JSONObject;
import org.jeecg.common.api.vo.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class TokenAuthInterceptor implements HandlerInterceptor {

    @Autowired
    private IntegrationProperties props;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object h) throws Exception {
        String uri = req.getRequestURI();
        IntegrationProperties.Token tokenCfg = props.getToken();
        String expected = null;
        if (uri.endsWith("/meter")) {
            expected = tokenCfg.getMeter();
        } else if (uri.endsWith("/equipment")) {
            expected = tokenCfg.getEquipment();
        }
        String token = req.getHeader("X-Integration-Token");
        if (expected == null || token == null || !token.equals(expected)) {
            resp.setStatus(HttpStatus.UNAUTHORIZED.value());
            resp.setContentType("application/json;charset=UTF-8");
            Result<Void> body = new Result<>();
            body.setSuccess(false);
            body.setCode(HttpStatus.UNAUTHORIZED.value());
            body.setMessage("对接令牌无效或接收未启用");
            resp.getWriter().write(JSONObject.toJSONString(body));
            return false;
        }
        return true;
    }
}
