package org.jeecg.modules.bems.integration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "bems.integration")
public class IntegrationProperties {
    private boolean enabled = false;
    private Token token = new Token();
    private String source = "sgai-bems";
    private Master master = new Master();
    private Push push = new Push();

    private GasOrHydrogen gas = new GasOrHydrogen();
    private GasOrHydrogen hydrogen = new GasOrHydrogen();
    private GasOrHydrogen point = new GasOrHydrogen();
    @Data
    public static class Token {
        private String meter;
        private String equipment;
    }

    @Data
    public static class Master {
        private String baseUrl;
        private String receivePath = "/master/integration/receive";
    }
    @Data
    public static class Push {
        private int timeoutSeconds = 5;
    }

    @Data
    public static class GasOrHydrogen {
        private String url;
        private String tagids;
    }
}
