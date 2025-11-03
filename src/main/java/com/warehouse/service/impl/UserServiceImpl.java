package com.warehouse.service.impl;

import com.warehouse.entity.User;
import com.warehouse.entity.UserRole;
import com.warehouse.repository.UserRepository;
import com.warehouse.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of UserService for managing users.
 */
@Service
@Transactional
public class UserServiceImpl implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        logger.debug("Finding user by username: {}", username);
        return userRepository.findByUsername(username);
    }

    @Override
    public List<User> listUsers() {
        logger.debug("Fetching all users");
        return userRepository.findAll();
    }

    @Override
    public User createUser(String username, String rawPassword, UserRole role) {
        logger.info("Creating new user: {}", username);
        if (userRepository.existsByUsername(username)) {
            logger.warn("Username already exists: {}", username);
            throw new IllegalArgumentException(com.warehouse.constants.BusinessMessages.USERNAME_ALREADY_EXISTS);
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setActive(true);
        User saved = userRepository.save(user);
        logger.info("User created successfully with id: {}", saved.getId());
        return saved;
    }

    @Override
    public void changeRole(Long userId, UserRole role) {
        logger.info("Changing role for user id: {} to role: {}", userId, role);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.warn("User not found with id: {}", userId);
                    return new IllegalArgumentException(com.warehouse.constants.BusinessMessages.USER_NOT_FOUND);
                });
        user.setRole(role);
        userRepository.save(user);
        logger.info("User role changed successfully");
    }

    @Override
    public void resetPassword(Long userId, String newRawPassword) {
        logger.info("Resetting password for user id: {}", userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> {
                    logger.warn("User not found with id: {}", userId);
                    return new IllegalArgumentException(com.warehouse.constants.BusinessMessages.USER_NOT_FOUND);
                });
        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
        userRepository.save(user);
        logger.info("User password reset successfully");
    }

    @Override
    public void deleteUser(Long userId) {
        logger.info("Deleting user with id: {}", userId);
        userRepository.deleteById(userId);
        logger.info("User deleted successfully with id: {}", userId);
    }
}

