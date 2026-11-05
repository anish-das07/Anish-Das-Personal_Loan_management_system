package com.example.plms.auth;

import com.example.plms.audit.AuditService;
import com.example.plms.common.ApiException;
import com.example.plms.common.PlmsProperties;
import com.example.plms.customer.CustomerService;
import com.example.plms.notification.MockMailbox;
import com.example.plms.notification.NotificationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Invalid email or password.";

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginThrottle throttle;
    private final CustomerService customers;
    private final NotificationService notifications;
    private final MockMailbox mailbox;
    private final AuditService audit;
    private final PlmsProperties.Security config;
    private final Clock clock;
    /** Compared against when the email is unknown, so login timing does not reveal registration. */
    private final String dummyHash;

    public AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens,
                       PasswordResetTokenRepository resetTokens, PasswordEncoder passwordEncoder,
                       JwtService jwtService, LoginThrottle throttle, CustomerService customers,
                       NotificationService notifications, MockMailbox mailbox, AuditService audit,
                       PlmsProperties properties, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.resetTokens = resetTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.throttle = throttle;
        this.customers = customers;
        this.notifications = notifications;
        this.mailbox = mailbox;
        this.audit = audit;
        this.config = properties.security();
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(TokenSupport.newOpaqueToken());
    }

    /** Public registration always creates a CUSTOMER, whatever the payload says. */
    @Transactional
    public AuthDtos.UserSummary register(AuthDtos.RegisterRequest request) {
        if (!Boolean.TRUE.equals(request.acceptedTerms())) {
            throw ApiException.badRequest("TERMS_NOT_ACCEPTED", "Accept the terms and privacy notice to continue.");
        }
        String email = UserAccount.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("ACCOUNT_EXISTS",
                    "An account could not be created with these details. Try signing in instead.");
        }
        UserAccount user = users.save(new UserAccount(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), EnumSet.of(Role.CUSTOMER), Instant.now(clock)));
        customers.createForUser(user.getId(), user.getFullName(), email, request.mobile());
        audit.record(user.getId(), "USER_REGISTERED", "USER", user.getId(), "SUCCESS", Map.of("role", "CUSTOMER"));
        notifications.notify(user.getId(), "WELCOME", "Welcome to PLMS", "Your customer account has been created.");
        return AuthDtos.UserSummary.from(user);
    }

    @Transactional
    public AuthDtos.TokenResponse login(AuthDtos.LoginRequest request) {
        String email = UserAccount.normalizeEmail(request.email());
        if (throttle.isBlocked(email)) {
            audit.recordIndependently(null, "LOGIN_THROTTLED", "USER", null, "DENIED", Map.of());
            throw ApiException.tooManyRequests("Too many failed sign-in attempts. Try again later.");
        }
        Optional<UserAccount> found = users.findByEmail(email);
        boolean passwordOk = passwordEncoder.matches(request.password(),
                found.map(UserAccount::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordOk || !found.get().isActive()) {
            throttle.recordFailure(email);
            audit.recordIndependently(found.map(UserAccount::getId).orElse(null), "LOGIN_FAILED", "USER",
                    found.map(UserAccount::getId).orElse(null), "DENIED", Map.of());
            throw ApiException.unauthorized(INVALID_CREDENTIALS);
        }
        throttle.reset(email);
        UserAccount user = found.get();
        audit.record(user.getId(), "LOGIN_SUCCEEDED", "USER", user.getId(), "SUCCESS", Map.of());
        return issueTokens(user, UUID.randomUUID().toString());
    }

    /**
     * Rotates a refresh token atomically. Reuse of an already-rotated or revoked token is treated as
     * replay: the whole token family is revoked and the request is rejected.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthDtos.TokenResponse refresh(String rawRefreshToken) {
        Instant now = Instant.now(clock);
        RefreshToken current = refreshTokens.findForUpdate(TokenSupport.sha256(rawRefreshToken))
                .orElseThrow(() -> ApiException.unauthorized("The session is no longer valid. Sign in again."));
        if (current.isRevoked()) {
            refreshTokens.findByFamilyId(current.getFamilyId()).forEach(t -> t.revoke(now, null));
            audit.record(current.getUserId(), "REFRESH_TOKEN_REPLAY", "REFRESH_TOKEN", current.getFamilyId(),
                    "DENIED", Map.of());
            throw ApiException.unauthorized("The session is no longer valid. Sign in again.");
        }
        if (current.isExpired(now)) {
            current.revoke(now, null);
            throw ApiException.unauthorized("The session has expired. Sign in again.");
        }
        UserAccount user = users.findById(current.getUserId())
                .filter(UserAccount::isActive)
                .orElseThrow(() -> ApiException.unauthorized("The session is no longer valid. Sign in again."));
        IssuedRefresh next = newRefreshToken(user.getId(), current.getFamilyId(), now);
        current.revoke(now, next.entity().getId());
        return new AuthDtos.TokenResponse(jwtService.issueAccessToken(user), next.raw(), "Bearer",
                jwtService.accessTokenTtlSeconds(), AuthDtos.UserSummary.from(user));
    }

    /** Revokes the presented refresh session (only if it belongs to the caller). */
    @Transactional
    public void logout(String userId, String rawRefreshToken) {
        Instant now = Instant.now(clock);
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.findForUpdate(TokenSupport.sha256(rawRefreshToken))
                    .filter(t -> t.getUserId().equals(userId))
                    .ifPresent(t -> refreshTokens.findByFamilyId(t.getFamilyId()).forEach(x -> x.revoke(now, null)));
        }
        audit.record(userId, "LOGOUT", "USER", userId, "SUCCESS", Map.of());
    }

    /** Always returns normally so that callers cannot learn whether an email is registered. */
    @Transactional
    public void requestPasswordReset(String rawEmail) {
        String email = UserAccount.normalizeEmail(rawEmail);
        users.findByEmail(email).filter(UserAccount::isActive).ifPresent(user -> {
            Instant now = Instant.now(clock);
            resetTokens.findByUserIdAndUsedAtIsNull(user.getId()).forEach(t -> t.markUsed(now));
            String raw = TokenSupport.newOpaqueToken();
            resetTokens.save(new PasswordResetToken(user.getId(), TokenSupport.sha256(raw),
                    now.plus(config.resetTokenTtl())));
            mailbox.deliverPasswordReset(email, raw);
            notifications.notify(user.getId(), "PASSWORD_RESET_REQUESTED", "Password reset requested",
                    "Password reset instructions were sent to your registered email address.");
            audit.record(user.getId(), "PASSWORD_RESET_REQUESTED", "USER", user.getId(), "SUCCESS", Map.of());
        });
    }

    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        Instant now = Instant.now(clock);
        PasswordResetToken token = resetTokens.findByTokenHash(TokenSupport.sha256(rawToken))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> ApiException.badRequest("INVALID_RESET_TOKEN",
                        "This reset link is invalid or has expired."));
        UserAccount user = users.findById(token.getUserId())
                .orElseThrow(() -> ApiException.badRequest("INVALID_RESET_TOKEN",
                        "This reset link is invalid or has expired."));
        token.markUsed(now);
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.findByUserIdAndRevokedAtIsNull(user.getId()).forEach(t -> t.revoke(now, null));
        throttle.reset(user.getEmail());
        audit.record(user.getId(), "PASSWORD_RESET_COMPLETED", "USER", user.getId(), "SUCCESS", Map.of());
    }

    // ---- Administration ----

    @Transactional
    public AuthDtos.UserSummary createStaffUser(String adminId, AuthDtos.CreateStaffRequest request) {
        String email = UserAccount.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw ApiException.conflict("ACCOUNT_EXISTS", "A user with this email already exists.");
        }
        if (request.roles().contains(Role.CUSTOMER)) {
            throw ApiException.badRequest("INVALID_ROLE", "Customers register through public registration.");
        }
        UserAccount user = users.save(new UserAccount(email, passwordEncoder.encode(request.password()),
                request.fullName().trim(), request.roles(), Instant.now(clock)));
        audit.record(adminId, "STAFF_USER_CREATED", "USER", user.getId(), "SUCCESS",
                Map.of("roles", request.roles().toString()));
        return AuthDtos.UserSummary.from(user);
    }

    @Transactional
    public AuthDtos.UserSummary updateUser(String adminId, String userId, AuthDtos.UpdateUserRequest request) {
        UserAccount user = users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (adminId.equals(userId)) {
            throw ApiException.forbidden("SELF_ADMINISTRATION", "Administrators cannot change their own access.");
        }
        if (request.roles() != null && !request.roles().isEmpty()) {
            Set<Role> roles = EnumSet.copyOf(request.roles());
            if (roles.contains(Role.CUSTOMER) && roles.size() > 1) {
                throw ApiException.badRequest("INVALID_ROLE", "CUSTOMER cannot be combined with staff roles.");
            }
            user.replaceRoles(roles);
        }
        if (request.status() != null) {
            user.setStatus(request.status());
            if (request.status() != UserAccount.Status.ACTIVE) {
                Instant now = Instant.now(clock);
                refreshTokens.findByUserIdAndRevokedAtIsNull(userId).forEach(t -> t.revoke(now, null));
            }
        }
        audit.record(adminId, "USER_UPDATED", "USER", userId, "SUCCESS",
                Map.of("roles", String.valueOf(request.roles()), "status", String.valueOf(request.status())));
        return AuthDtos.UserSummary.from(user);
    }

    @Transactional(readOnly = true)
    public List<AuthDtos.UserSummary> listUsers() {
        return users.findAllByOrderByCreatedAtAsc().stream().map(AuthDtos.UserSummary::from).toList();
    }

    private AuthDtos.TokenResponse issueTokens(UserAccount user, String familyId) {
        IssuedRefresh refresh = newRefreshToken(user.getId(), familyId, Instant.now(clock));
        return new AuthDtos.TokenResponse(jwtService.issueAccessToken(user), refresh.raw(), "Bearer",
                jwtService.accessTokenTtlSeconds(), AuthDtos.UserSummary.from(user));
    }

    private IssuedRefresh newRefreshToken(String userId, String familyId, Instant now) {
        String raw = TokenSupport.newOpaqueToken();
        RefreshToken entity = refreshTokens.save(new RefreshToken(userId, familyId, TokenSupport.sha256(raw),
                now.plus(config.refreshTokenTtl()), now));
        return new IssuedRefresh(raw, entity);
    }

    private record IssuedRefresh(String raw, RefreshToken entity) {
    }
}
