package com.strongwine.strongwine.service;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service class for User business logic
 */
@Service
@Transactional
public class UserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    /**
     * Get all users
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    public org.springframework.data.domain.Page<User> getAllUsersPage(org.springframework.data.domain.Pageable pageable) {
        return userRepository.findAll(pageable);
    }
    
    public long countUsers() {
        return userRepository.count();
    }
    
    /**
     * Get user by ID
     */
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }
    
    /**
     * Get user by username
     */
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    /**
     * Create a new user
     */
    public User createUser(User user) {
        if (user == null) {
            throw new RuntimeException("User payload is required");
        }

        String username = normalizeRequired(user.getUsername(), "Username is required");
        String role = normalizeRequired(user.getRole(), "Role is required");
        String password = normalizeRequired(user.getPassword(), "Password is required");
        String email = normalizeOptionalEmail(user.getEmail());

        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username already exists");
        }
        if (email != null && userRepository.existsByEmail(email)) {
            throw new RuntimeException("Email already exists");
        }

        user.setUsername(username);
        user.setEmail(email);
        user.setRole(role);
        // Encode password before saving
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.save(user);
    }
    
    /**
     * Update an existing user
     */
    public User updateUser(Long id, User userDetails) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + id));

        if (userDetails == null) {
            throw new RuntimeException("User details are required");
        }

        String username = normalizeRequired(userDetails.getUsername(), "Username is required");
        String role = normalizeRequired(userDetails.getRole(), "Role is required");
        String email = normalizeOptionalEmail(userDetails.getEmail());

        if (userRepository.existsByUsernameAndIdNot(username, id)) {
            throw new RuntimeException("Username already exists");
        }
        if (email != null && userRepository.existsByEmailAndIdNot(email, id)) {
            throw new RuntimeException("Email already exists");
        }
        
        user.setUsername(username);
        user.setEmail(email);
        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDetails.getPassword().trim()));
        }
        user.setRole(role);
        
        return userRepository.save(user);
    }
    
    /**
     * Delete a user
     */
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("User not found with id: " + id);
        }

        try {
            userRepository.deleteById(id);
            userRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new RuntimeException("Cannot delete this user because it is referenced by existing records");
        }
    }
    
    /**
     * Check if username exists
     */
    public boolean usernameExists(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean emailExists(String email) {
        String normalized = normalizeOptionalEmail(email);
        return normalized != null && userRepository.existsByEmail(normalized);
    }

    public boolean isValidEmailFormat(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email.trim().toLowerCase()).matches();
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException(message);
        }
        return value.trim();
    }

    private String normalizeOptionalEmail(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new RuntimeException("Email format is invalid");
        }
        return normalized;
    }
}





