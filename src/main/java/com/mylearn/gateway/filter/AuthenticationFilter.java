package com.mylearn.gateway.filter;

import com.mylearn.common.dto.auth.AuthValidationResponse;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Custom Gateway Filter that validates JWT tokens with the auth-service before routing requests to
 * downstream microservices.
 */
@Component
@Slf4j
public class AuthenticationFilter
    extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String HEADER_X_USER_ID = "X-User-Id";
  private static final String HEADER_X_USER_ROLE = "X-User-Role";

  private final WebClient.Builder webClientBuilder;

  @Value("${app.services.auth-validation-url}")
  private String authValidationUrl;

  public AuthenticationFilter(WebClient.Builder webClientBuilder) {
    super(Config.class);
    this.webClientBuilder = webClientBuilder;
  }

  public static class Config {
    // Configuration properties for the filter if needed
  }

  @Override
  public GatewayFilter apply(Config config) {
    return (exchange, chain) -> {
      ServerHttpRequest request = exchange.getRequest();

      // 1. Check if Authorization header is present and valid
      if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
        log.warn("Missing Authorization header for request: {}", request.getPath());
        return Mono.error(
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization Header"));
      }

      String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

      // Validate the 'Bearer ' prefix at the gateway level to fail fast
      if (Objects.isNull(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
        log.warn("Invalid Authorization header format for request: {}", request.getPath());
        return Mono.error(
            new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid Authorization Header Format"));
      }

      // 2. Call Auth-Service to validate the token
      return webClientBuilder
          .build()
          .get()
          .uri(authValidationUrl)
          .header(HttpHeaders.AUTHORIZATION, authHeader)
          .retrieve()
          .bodyToMono(AuthValidationResponse.class)
          .flatMap(
              response -> {
                log.debug("Token validated for user: {}", response.getUserId());

                // 3. Mutate the request to add User context headers
                // Downstream services will use these headers (X-User-Id, X-User-Role)
                ServerHttpRequest mutatedRequest =
                    exchange
                        .getRequest()
                        .mutate()
                        .header(HEADER_X_USER_ID, response.getUserId().toString())
                        .header(HEADER_X_USER_ROLE, response.getRole().name())
                        .build();

                return chain.filter(exchange.mutate().request(mutatedRequest).build());
              })
          .onErrorResume(
              e -> {
                log.error("Authentication failed: {}", e.getMessage());
                return Mono.error(
                    new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid or Expired Token"));
              });
    };
  }
}
