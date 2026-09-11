package org.jeecg.modules.bems.dataRead.util;

import com.sunwayland.pspace.PSpaceClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
@Slf4j
@Component
public class PspaceUtils {
    @Value("${pspace.host:192.168.3.63}")
    private String host;

    @Value("${pspace.port:8889}")
    private int port;

    @Value("${pspace.username:admin}")
    private String username;

    @Value("${pspace.password:admin888}")
    private String password;

    @Value("${pspace.mock:false}")
    private boolean mock;


    /**
     * pspace 客户端，启动连接成功后即可供各包直接引用。
     * 注意：mock=true 且连接失败时为 null，使用时需判空。
     */
    public volatile PSpaceClient client;

    /**
     * 建立连接（幂等：已连接则直接返回）
     */
    public synchronized PSpaceClient connect() {
        if (client != null) {
            return client;
        }
        client = PSpaceClient.getInstance(
                host,
                port,
                username,
                password);
        try {
            client.connect();
            log.info("录取数据链接成功！地址={}",host);
        } catch (Exception e) {
            client = null;
            throw e;
        }
        return client;
    }

    /**
     * 项目启动时建立连接：
     * mock=false 连接失败则抛出异常阻断启动（正式环境快速暴露问题）；
     * mock=true  连接失败仅告警不阻断启动，client 保持 null，其它包引用时自行判空/走 mock 逻辑。
     */
    @PostConstruct
    public void init() {
        try {
            connect();
        } catch (Exception e) {
            if (mock) {
                log.error("项目启动时 pspace 连接失败(host={}, port={}), mock=true 不阻断启动, 后续调用 connect() 将自动重试", host, port, e);
            } else {
                throw new IllegalStateException(
                        String.format("项目启动时 pspace 连接失败(host=%s, port=%s), mock=false 已阻断启动", host, port), e);
            }
        }
    }
}
