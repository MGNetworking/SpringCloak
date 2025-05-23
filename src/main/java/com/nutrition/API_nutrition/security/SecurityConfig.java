package com.nutrition.API_nutrition.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Permet la gestion du Flux de traitement d'une requête.
 * Ces beans sont objets de configuration
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Active les annotations @PreAuthorize, @PostAuthorize,
public class SecurityConfig {

    /**
     * Lorsqu'une requête HTTP arrive à votre application :
     * <ul>
     *     <li>La requête passe d'abord par la chaîne de filtres de Spring Security (définie via {@link SecurityFilterChain}).</li>
     *     <li>Pour une API protégée par JWT, le filtre {@code BearerTokenAuthenticationFilter} (fourni par Spring) intercepte la requête.</li>
     *     <li>Ce filtre extrait le token JWT depuis l'en-tête HTTP {@code Authorization: Bearer [token]}.</li>
     *     <li>Le token est ensuite validé (signature, expiration, audience, etc.) par un {@code JwtDecoder} configuré automatiquement.</li>
     *     <li>Si le token est valide, les informations qu'il contient (claims) sont converties en un objet {@code Authentication}
     *         grâce à un {@code JwtAuthenticationConverter} personnalisé (comme {@code keycloakJwtAuthenticationConverter()}).</li>
     *     <li>Cette {@code Authentication} est placée dans le {@code SecurityContext}, rendant l'utilisateur authentifié dans le reste du traitement.</li>
     *     <li>Spring Security applique ensuite les règles d'autorisation configurées (via {@code authorizeHttpRequests}).</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable) // Pour une API REST
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)) // Permet les iframes pour H2
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/h2-console/**",
                                "/api/v1/auth/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-resources/**",
                                "/webjars/**")
                        .permitAll() // Endpoints d'authentification publics
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(keycloakJwtAuthenticationConverter())
                        )
                )
                .build();
    }

    /**
     * Bean définissant un convertisseur d'authentification JWT pour l'intégration avec Keycloak.
     * <p>
     * Ce convertisseur extrait les rôles présents dans le claim "realm_access" du token JWT émis par Keycloak,
     * puis les transforme en instances de {@link SimpleGrantedAuthority} en appliquant le préfixe "ROLE_",
     * conformément à la convention de Spring Security.
     * <p>
     * Il permet ainsi à Spring Security de créer une instance de {@link AbstractAuthenticationToken}
     * à partir d'un token {@link Jwt} contenant les claims Keycloak.
     *
     * <p>Le processus de conversion suit les étapes suivantes :
     * <ul>
     *     <li>1. Reçoit un objet {@link Jwt} contenant les claims décodés du token</li>
     *     <li>2. Recherche le claim nommé {@code realm_access}, propre à Keycloak</li>
     *     <li>3. Extrait la liste des rôles à partir de ce claim</li>
     *     <li>4. Convertit chaque rôle en {@link SimpleGrantedAuthority} avec le préfixe {@code ROLE_}</li>
     *     <li>5. Retourne cette liste d'autorités, utilisée par Spring Security pour les vérifications d'accès</li>
     * </ul>
     *
     * @return un convertisseur JWT en {@link AbstractAuthenticationToken} prêt à être utilisé par Spring Security
     */
    @Bean
    public Converter<Jwt, ? extends AbstractAuthenticationToken> keycloakJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess == null) {
                return Collections.emptyList();
            }

            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) realmAccess.get("roles");
            if (roles == null) {
                return Collections.emptyList();
            }

            return roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
                    .collect(Collectors.toList());
        });
        return converter;
    }
}
