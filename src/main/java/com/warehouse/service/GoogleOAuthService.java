package com.warehouse.service;

import java.util.Map;

public interface GoogleOAuthService {
    Map<String, Object> exchangeCodeForUserInfo(String code, String redirectUri);
}
