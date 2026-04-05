package com.warehouse.service.impl;

import com.warehouse.config.GoogleOAuthProperties;
import com.warehouse.exception.ErrorCode;
import com.warehouse.exception.WarehouseManagementException;
import com.warehouse.service.GoogleOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class GoogleOAuthServiceImpl implements GoogleOAuthService {

    private static final Logger logger = LoggerFactory.getLogger(GoogleOAuthServiceImpl.class);
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final GoogleOAuthProperties properties;
    private final RestTemplate restTemplate;

    public GoogleOAuthServiceImpl(GoogleOAuthProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> exchangeCodeForUserInfo(String code, String redirectUri) {
        if (properties.getClientId().isBlank() || properties.getClientSecret().isBlank()) {
            throw new WarehouseManagementException(ErrorCode.VALIDATION_ERROR,
                "Google OAuth yapilandirilmamis. GOOGLE_CLIENT_ID ve GOOGLE_CLIENT_SECRET ayarlayin.");
        }

        String effectiveRedirectUri = (redirectUri != null && !redirectUri.isBlank())
            ? redirectUri : properties.getRedirectUri();

        // Step 1: Exchange authorization code for access token
        HttpHeaders tokenHeaders = new HttpHeaders();
        tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> tokenParams = new LinkedMultiValueMap<>();
        tokenParams.add("code", code);
        tokenParams.add("client_id", properties.getClientId());
        tokenParams.add("client_secret", properties.getClientSecret());
        tokenParams.add("redirect_uri", effectiveRedirectUri);
        tokenParams.add("grant_type", "authorization_code");

        Map<String, Object> tokenResponse;
        try {
            ResponseEntity<Map> tokenEntity = restTemplate.exchange(
                TOKEN_URL, HttpMethod.POST,
                new HttpEntity<>(tokenParams, tokenHeaders),
                Map.class
            );
            tokenResponse = tokenEntity.getBody();
        } catch (Exception e) {
            String detail = e.getMessage();
            if (e instanceof org.springframework.web.client.HttpClientErrorException hce) {
                detail = hce.getResponseBodyAsString();
            }
            logger.error("Google token exchange failed: {} — detail: {}", e.getMessage(), detail);
            throw new WarehouseManagementException(ErrorCode.AUTH_ERROR,
                "Google kimlik doğrulama başarısız: " + (detail != null && detail.length() < 200 ? detail : "Lütfen tekrar deneyin."));
        }

        if (tokenResponse == null || !tokenResponse.containsKey("access_token")) {
            throw new WarehouseManagementException(ErrorCode.AUTH_ERROR,
                "Google'dan gecerli access token alinamadi.");
        }

        String accessToken = (String) tokenResponse.get("access_token");

        // Step 2: Fetch user info with access token
        HttpHeaders userInfoHeaders = new HttpHeaders();
        userInfoHeaders.setBearerAuth(accessToken);

        Map<String, Object> userInfo;
        try {
            ResponseEntity<Map> userInfoEntity = restTemplate.exchange(
                USERINFO_URL, HttpMethod.GET,
                new HttpEntity<>(userInfoHeaders),
                Map.class
            );
            userInfo = userInfoEntity.getBody();
        } catch (Exception e) {
            logger.error("Google userinfo fetch failed: {}", e.getMessage());
            throw new WarehouseManagementException(ErrorCode.AUTH_ERROR,
                "Google kullanici bilgileri alinamadi.");
        }

        if (userInfo == null || !userInfo.containsKey("email")) {
            throw new WarehouseManagementException(ErrorCode.AUTH_ERROR,
                "Google hesabindan email bilgisi alinamadi.");
        }

        logger.info("Google OAuth successful for email: {}", userInfo.get("email"));
        return userInfo;
    }
}
