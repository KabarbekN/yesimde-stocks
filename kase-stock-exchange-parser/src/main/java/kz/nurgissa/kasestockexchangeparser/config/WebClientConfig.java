package kz.nurgissa.kasestockexchangeparser.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

@Configuration
public class WebClientConfig {

    private static final int MAX_MEMORY_SIZE = 16 * 1024 * 1024; // 16 MB

    @Bean(name = "kaseWebClient")
    @Primary
    public WebClient kaseWebClient() {
        return WebClient.builder()
                .baseUrl("https://kase.kz/api")
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE))
                        .build())
                .build();
    }

    @Bean(name = "aixWebClient")
    public WebClient aixWebClient() {
        return WebClient.builder()
                .baseUrl("https://market-backend.aixkz.com/api")
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .defaultHeader("Accept", "application/json, text/plain, */*")
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE))
                        .build())
                .build();
    }
}
