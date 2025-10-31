package com.warehouse.service;

import com.warehouse.entity.User;
import com.warehouse.entity.UserRole;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing users.
 */
public interface UserService {

    Optional<User> findByUsername(String username);

    List<User> listUsers();

    User createUser(String username, String rawPassword, UserRole role);

    void changeRole(Long userId, UserRole role);

    void resetPassword(Long userId, String newRawPassword);

    void deleteUser(Long userId);
}
