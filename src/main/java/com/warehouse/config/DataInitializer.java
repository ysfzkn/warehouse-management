package com.warehouse.config;

import com.warehouse.entity.UserRole;
import com.warehouse.repository.UserRepository;
import com.warehouse.service.UserService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer {

    private final UserRepository userRepository;
    private final UserService userService;
    private final AuthProperties authProperties;

    public DataInitializer(UserRepository userRepository, UserService userService, AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.authProperties = authProperties;
    }

    @PostConstruct
    public void init() {
        if (userRepository.count() == 0) {
            userService.createUser(authProperties.getUsername(), authProperties.getPassword(), UserRole.ADMIN);
        }
    }
}


