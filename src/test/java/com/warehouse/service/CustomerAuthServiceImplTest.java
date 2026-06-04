package com.warehouse.service;

import com.warehouse.dto.store.CustomerLoginRequest;
import com.warehouse.dto.store.CustomerLoginResponse;
import com.warehouse.dto.store.CustomerRegisterRequest;
import com.warehouse.entity.Customer;
import com.warehouse.entity.CustomerRefreshToken;
import com.warehouse.enums.CustomerStatus;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.config.SecurityProperties;
import com.warehouse.repository.CustomerRepository;
import com.warehouse.repository.CustomerRefreshTokenRepository;
import com.warehouse.security.JwtService;
import com.warehouse.service.impl.CustomerAuthServiceImpl;
import com.warehouse.testutil.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomerAuthServiceImplTest {

    @Mock private CustomerRepository customerRepo;
    @Mock private CustomerRefreshTokenRepository refreshTokenRepo;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private GoogleOAuthService googleOAuthService;
    @Mock private com.warehouse.service.EmailService emailService;

    private SecurityProperties securityProperties;
    private CustomerAuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        authService = new CustomerAuthServiceImpl(customerRepo, refreshTokenRepo, passwordEncoder, jwtService, googleOAuthService, securityProperties, emailService);
    }

    @Test
    void should_register_customer_successfully() {
        CustomerRegisterRequest req = new CustomerRegisterRequest();
        req.setEmail("new@test.com");
        req.setPassword("Test1234");
        req.setFirstName("Test");
        req.setLastName("User");
        req.setKvkkConsent(true);

        when(customerRepo.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("Test1234")).thenReturn("hashed");
        when(customerRepo.save(any(Customer.class))).thenAnswer(inv -> { Customer c = inv.getArgument(0); c.setId(1L); return c; });
        when(jwtService.generateCustomerToken(anyLong(), anyString())).thenReturn("jwt-token");
        when(refreshTokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerLoginResponse result = authService.register(req);

        assertThat(result.getToken()).isEqualTo("jwt-token");
        assertThat(result.getEmail()).isEqualTo("new@test.com");
        verify(customerRepo).save(any());
    }

    @Test
    void should_throw_for_duplicate_email() {
        CustomerRegisterRequest req = new CustomerRegisterRequest();
        req.setEmail("dup@test.com");
        req.setPassword("Test1234");
        req.setFirstName("T");
        req.setLastName("U");
        req.setKvkkConsent(true);

        when(customerRepo.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(req))
            .isInstanceOf(WarehouseManagementException.class)
            .satisfies(e -> assertThat(((WarehouseManagementException)e).getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_KEY));
    }

    @Test
    void should_throw_for_weak_password() {
        CustomerRegisterRequest req = new CustomerRegisterRequest();
        req.setEmail("x@t.com");
        req.setPassword("weak");
        req.setFirstName("T");
        req.setLastName("U");
        req.setKvkkConsent(true);

        assertThatThrownBy(() -> authService.register(req))
            .isInstanceOf(WarehouseManagementException.class)
            .satisfies(e -> assertThat(((WarehouseManagementException)e).getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void should_throw_when_kvkk_not_accepted() {
        CustomerRegisterRequest req = new CustomerRegisterRequest();
        req.setEmail("x@t.com");
        req.setPassword("Test1234");
        req.setFirstName("T");
        req.setLastName("U");
        req.setKvkkConsent(false);

        when(customerRepo.existsByEmail("x@t.com")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(req))
            .isInstanceOf(WarehouseManagementException.class);
    }

    @Test
    void should_login_successfully() {
        Customer customer = TestDataFactory.createCustomer();
        CustomerLoginRequest req = new CustomerLoginRequest();
        req.setEmail(customer.getEmail());
        req.setPassword("Test1234");

        when(customerRepo.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("Test1234", customer.getPasswordHash())).thenReturn(true);
        when(customerRepo.save(any())).thenReturn(customer);
        when(jwtService.generateCustomerToken(anyLong(), anyString())).thenReturn("jwt");
        when(refreshTokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerLoginResponse result = authService.login(req, "127.0.0.1");

        assertThat(result.getToken()).isEqualTo("jwt");
        verify(customerRepo).save(argThat(c -> c.getFailedLoginCount() == 0));
    }

    @Test
    void should_throw_for_wrong_password() {
        Customer customer = TestDataFactory.createCustomer();
        CustomerLoginRequest req = new CustomerLoginRequest();
        req.setEmail(customer.getEmail());
        req.setPassword("wrong");

        when(customerRepo.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("wrong", customer.getPasswordHash())).thenReturn(false);
        when(customerRepo.save(any())).thenReturn(customer);

        assertThatThrownBy(() -> authService.login(req, "127.0.0.1"))
            .isInstanceOf(WarehouseManagementException.class);

        verify(customerRepo).save(argThat(c -> c.getFailedLoginCount() == 1));
    }

    @Test
    void should_lock_account_after_5_failed_attempts() {
        Customer customer = TestDataFactory.createCustomer();
        customer.setFailedLoginCount(4);
        CustomerLoginRequest req = new CustomerLoginRequest();
        req.setEmail(customer.getEmail());
        req.setPassword("wrong");

        when(customerRepo.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
        when(passwordEncoder.matches("wrong", customer.getPasswordHash())).thenReturn(false);
        when(customerRepo.save(any())).thenReturn(customer);

        assertThatThrownBy(() -> authService.login(req, "127.0.0.1"));

        verify(customerRepo).save(argThat(c -> c.getLockedUntil() != null));
    }

    @Test
    void should_throw_when_account_locked() {
        Customer customer = TestDataFactory.createCustomer();
        customer.setLockedUntil(LocalDateTime.now().plusMinutes(10));
        CustomerLoginRequest req = new CustomerLoginRequest();
        req.setEmail(customer.getEmail());
        req.setPassword("Test1234");

        when(customerRepo.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> authService.login(req, "127.0.0.1"))
            .isInstanceOf(WarehouseManagementException.class);
    }

    @Test
    void should_throw_when_account_blacklisted() {
        Customer customer = TestDataFactory.createCustomer();
        customer.setStatus(CustomerStatus.BLACKLISTED);
        CustomerLoginRequest req = new CustomerLoginRequest();
        req.setEmail(customer.getEmail());
        req.setPassword("Test1234");

        when(customerRepo.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> authService.login(req, "127.0.0.1"))
            .isInstanceOf(WarehouseManagementException.class);
    }

    @Test
    void should_refresh_token_and_revoke_old() {
        Customer customer = TestDataFactory.createCustomer();
        CustomerRefreshToken oldToken = TestDataFactory.createRefreshToken(customer);

        when(refreshTokenRepo.findByTokenAndRevokedAtIsNull(oldToken.getToken())).thenReturn(Optional.of(oldToken));
        when(jwtService.generateCustomerToken(anyLong(), anyString())).thenReturn("new-jwt");
        when(refreshTokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerLoginResponse result = authService.refreshToken(oldToken.getToken());

        assertThat(result.getToken()).isEqualTo("new-jwt");
        assertThat(oldToken.getRevokedAt()).isNotNull();
    }

    @Test
    void should_throw_for_expired_refresh_token() {
        Customer customer = TestDataFactory.createCustomer();
        CustomerRefreshToken token = TestDataFactory.createRefreshToken(customer);
        token.setExpiresAt(LocalDateTime.now().minusDays(1));

        when(refreshTokenRepo.findByTokenAndRevokedAtIsNull(token.getToken())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> authService.refreshToken(token.getToken()))
            .isInstanceOf(WarehouseManagementException.class);
    }

    @Test
    void should_login_with_google_new_user() {
        when(googleOAuthService.exchangeCodeForUserInfo("code123", "http://localhost"))
            .thenReturn(Map.of("email", "google@test.com", "given_name", "Google", "family_name", "User"));
        when(customerRepo.findByEmail("google@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(customerRepo.save(any(Customer.class))).thenAnswer(inv -> { Customer c = inv.getArgument(0); c.setId(1L); return c; });
        when(jwtService.generateCustomerToken(anyLong(), anyString())).thenReturn("google-jwt");
        when(refreshTokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerLoginResponse result = authService.loginWithGoogle("code123", "http://localhost", "127.0.0.1");

        assertThat(result.getToken()).isEqualTo("google-jwt");
        verify(customerRepo, atLeastOnce()).save(argThat(c -> c.isEmailVerified()));
    }

    @Test
    void should_login_with_google_existing_user() {
        Customer existing = TestDataFactory.createCustomer();
        when(googleOAuthService.exchangeCodeForUserInfo("code", "http://localhost"))
            .thenReturn(Map.of("email", existing.getEmail(), "given_name", "G", "family_name", "U"));
        when(customerRepo.findByEmail(existing.getEmail())).thenReturn(Optional.of(existing));
        when(customerRepo.save(any())).thenReturn(existing);
        when(jwtService.generateCustomerToken(anyLong(), anyString())).thenReturn("jwt");
        when(refreshTokenRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomerLoginResponse result = authService.loginWithGoogle("code", "http://localhost", "127.0.0.1");

        assertThat(result.getToken()).isEqualTo("jwt");
        verify(customerRepo, never()).save(argThat(c -> c.getId() == null)); // should NOT create new
    }
}
