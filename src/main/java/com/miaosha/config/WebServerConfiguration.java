package com.miaosha.config;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatConnectorCustomizer;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

// 当spring容器内没有TomcatEmbeddedServletContainerFactory这个bean时，会加载此bean
@Component
public class WebServerConfiguration implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {


    @Override
    public void customize(ConfigurableWebServerFactory factory) {
        // 使用工厂类定制化tomcat connector
        ((TomcatServletWebServerFactory)factory).addConnectorCustomizers(new TomcatConnectorCustomizer() {
            @Override
            public void customize(Connector connector) {
                Http11NioProtocol http11NioProtocol= (Http11NioProtocol) connector.getProtocolHandler();
                // 定制keep-alive-timeout=30秒
                http11NioProtocol.setKeepAliveTimeout(30000);
                // 当客户端发送1000个请求后会自动断开keep-alive连接
                http11NioProtocol.setMaxKeepAliveRequests(1000);
            }
        });

    }
}
