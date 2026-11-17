package com.strongwine.strongwine.security;

import com.strongwine.strongwine.entity.User;
import com.strongwine.strongwine.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

/**
 * Custom UserDetailsService implementation for Spring Security
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    
    @Autowired
    private UserRepository userRepository;
    
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("User not found: " + username);
        }
        String trimmed = username.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        User user = userRepository.findByUsername(trimmed)
                .or(() -> userRepository.findByEmail(lower))
                .or(() -> lower.equals(trimmed) ? Optional.empty() : userRepository.findByEmail(trimmed))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        
        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPassword())
            .roles(user.getRole())
            .build();
    }
}





