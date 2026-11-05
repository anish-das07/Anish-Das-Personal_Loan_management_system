package com.example.plms.auth;

import com.example.plms.common.PlmsProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Issues short-lived access tokens. Roles are taken from the stored account, never from the client. */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final PlmsProperties.Security config;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, PlmsProperties properties, Clock clock) {
        this.encoder = encoder;
        this.config = properties.security();
        this.clock = clock;
    }

    public String issueAccessToken(UserAccount user) {
        Instant now = Instant.now(clock);
        List<String> roles = user.getRoles().stream().map(Enum::name).sorted().toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(config.issuer())
                .audience(List.of(config.audience()))
                .subject(user.getId())
                .issuedAt(now)
                .expiresAt(now.plus(config.accessTokenTtl()))
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public long accessTokenTtlSeconds() {
        return config.accessTokenTtl().toSeconds();
    }
}
