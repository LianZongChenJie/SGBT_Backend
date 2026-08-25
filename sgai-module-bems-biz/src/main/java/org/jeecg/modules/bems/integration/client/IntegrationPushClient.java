package org.jeecg.modules.bems.integration.client;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.bems.integration.config.IntegrationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class IntegrationPushClient {

    private final RestTemplate restTemplate;
    private final IntegrationProperties props;

    public IntegrationPushClient(@Qualifier("integrationRestTemplate") RestTemplate restTemplate,
                                 IntegrationProperties props) {
        this.restTemplate = restTemplate;
        this.props = props;
    }

    /** @return {httpStatus, body} */
    public Map<String, Object> postReceive(Map<String, Object> payload, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Integration-Token", token);
        headers.set("X-Source", props.getSource());
        String url = props.getMaster().getBaseUrl() + props.getMaster().getReceivePath();
        ResponseEntity<Map> resp = restTemplate.postForEntity(url,
                new HttpEntity<>(payload, headers), Map.class);
        return Map.of(
                "httpStatus", resp.getStatusCodeValue(),
                "body", resp.getBody() == null ? Map.of() : resp.getBody()
        );
    }
}
