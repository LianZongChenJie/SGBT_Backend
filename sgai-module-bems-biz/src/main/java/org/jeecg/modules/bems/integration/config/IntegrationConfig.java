package org.jeecg.modules.bems.integration.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;

@Configuration
public class IntegrationConfig implements WebMvcConfigurer {

    @Autowired
    private TokenAuthInterceptor tokenAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tokenAuthInterceptor)
                .addPathPatterns("/integration/receive/**");
    }


    @Bean("integrationRestTemplate")
    public RestTemplate integrationRestTemplate(IntegrationProperties props) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        int ms = props.getPush().getTimeoutSeconds() * 1000;
        f.setConnectTimeout(ms);
        f.setReadTimeout(ms);
        return new RestTemplate(f);
    }

    @Bean("integrationPushExecutor")
    public Executor integrationPushExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("integration-push-");
        ex.initialize();
        return ex;
    }
}
