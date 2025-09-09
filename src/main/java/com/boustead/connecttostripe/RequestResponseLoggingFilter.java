package com.boustead.connecttostripe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class RequestResponseLoggingFilter implements WebFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);

    @Value("${environment}")
    private String environment;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!environment.equals("PROD")) {
            String method = exchange.getRequest().getMethod().name();
            String uri = exchange.getRequest().getURI().toString();
            logger.info("Incoming request: {} {}", method, uri);

            return chain.filter(exchange)
                    .doFinally(signalType -> {
                        int statusCode = exchange.getResponse().getStatusCode() != null ? 
                            exchange.getResponse().getStatusCode().value() : 0;
                        logger.info("Outgoing response: {}", statusCode);
                    });
        }

        return chain.filter(exchange);
    }
}
