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
    private GasOrHydrogen eldb = new GasOrHydrogen();
    private GasOrHydrogen ysclq = new GasOrHydrogen();
    private GasOrHydrogen gl1 = new GasOrHydrogen();
    private GasOrHydrogen gl2 = new GasOrHydrogen();
    private GasOrHydrogen gl3 = new GasOrHydrogen();
    private GasOrHydrogen glfj = new GasOrHydrogen();
    private GasOrHydrogen bfxtscl = new GasOrHydrogen();
    private GasOrHydrogen ld = new GasOrHydrogen();
    private GasOrHydrogen cqhq = new GasOrHydrogen();
    private GasOrHydrogen cqqqjyq = new GasOrHydrogen();
    private GasOrHydrogen gf = new GasOrHydrogen();
    private GasOrHydrogen grxtzj = new GasOrHydrogen();
    private GasOrHydrogen bems = new GasOrHydrogen();
    private GasOrHydrogen nyz = new GasOrHydrogen();
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
