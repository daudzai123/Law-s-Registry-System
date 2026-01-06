package com.mcit.service;

import com.mcit.dto.ChangePasswordRequest;
import com.mcit.dto.UserResponseDTO;
import com.mcit.entity.User;
import com.mcit.repo.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Slf4j
@Service
public class MyUserDetailService implements UserDetailsService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;


    @Autowired
    public MyUserDetailService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        log.info("[AUTH] Trying login with username/email: {}", identifier);

        Optional<User> optionalUser = repository.findByUsername(identifier);
        if (optionalUser.isEmpty()) {
            log.info("[AUTH] Not found by username. Trying email: {}", identifier);
            optionalUser = repository.findByEmail(identifier);
        }

        User user = optionalUser.orElseThrow(() ->
                new UsernameNotFoundException("User not found with username or email: " + identifier));

        log.info("[AUTH] Found user: username='{}', email='{}', role='{}'",
                user.getUsername(), user.getEmail(), user.getRole());

        return new org.springframework.security.core.userdetails.User(
                identifier,  // return the identifier used for login to keep consistent principal
                user.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }

    public void changePassword(String identifier, ChangePasswordRequest request) {
        Optional<User> userOpt = repository.findByUsername(identifier);
        if (userOpt.isEmpty()) {
            userOpt = repository.findByEmail(identifier);
        }
        User user = userOpt.orElseThrow(() -> new UsernameNotFoundException("User not found with identifier: " + identifier));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        repository.save(user);
    }

    // ========== PAGINATION & SEARCH METHODS ==========

    public Page<User> getUsersPaginated(int page, int size, String[] sort) {

        Sort sortOrder = Sort.by(
                Sort.Direction.fromString(sort[1]),
                sort[0]
        );

        Pageable pageable = PageRequest.of(page, size, sortOrder);
        return repository.findAll(pageable);
    }

    public Page<User> getUsersSorted(int page, int size, String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
        return repository.findAll(pageable);
    }

    public Page<User> searchUsersPaginated(String username, int page, int size, String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
        return repository.findByUsernameContainingIgnoreCase(username, pageable);
    }

    public User findByUsername(String username) {
        return repository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }
}
