package kz.nurgissa.kasestockexchangeparser.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class WebClientConfig {

    private static final int MAX_MEMORY_SIZE = 16 * 1024 * 1024; // 16 MB

    private HttpClient createHttpClient() {
        ConnectionProvider provider = ConnectionProvider.builder("custom-http-pool")
                .maxConnections(100)
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(2))
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .evictInBackground(Duration.ofSeconds(60))
                .build();

        return HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(15));
    }

    @Bean(name = "kaseWebClient")
    @Primary
    public WebClient kaseWebClient() {
        return WebClient.builder()
                .baseUrl("https://kase.kz/api")
                .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE))
                        .build())
                .build();
    }

    @Bean(name = "aixWebClient")
    public WebClient aixWebClient() {
        return WebClient.builder()
                .baseUrl("https://market-backend.aixkz.com/api")
                .clientConnector(new ReactorClientHttpConnector(createHttpClient()))
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .defaultHeader("Accept", "application/json, text/plain, */*")
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(MAX_MEMORY_SIZE))
                        .build())
                .build();
    }
}
