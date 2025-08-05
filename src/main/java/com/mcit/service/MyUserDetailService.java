package com.mcit.service;

import com.mcit.dto.ChangePasswordRequest;
import com.mcit.entity.MyUser;
import com.mcit.repo.MyUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
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

    private final MyUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public MyUserDetailService(MyUserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        log.info("[AUTH] Trying login with username/email: {}", identifier);

        Optional<MyUser> optionalUser = repository.findByUsername(identifier);
        if (optionalUser.isEmpty()) {
            log.info("[AUTH] Not found by username. Trying email: {}", identifier);
            optionalUser = repository.findByEmail(identifier);
        }

        MyUser user = optionalUser.orElseThrow(() ->
                new UsernameNotFoundException("User not found with username or email: " + identifier));

        log.info("[AUTH] Found user: username='{}', email='{}', role='{}'",
                user.getUsername(), user.getEmail(), user.getRole());

        return new User(
                identifier,  // return the identifier used for login to keep consistent principal
                user.getPassword(),
                Collections.singleton(new SimpleGrantedAuthority(user.getRole().name()))
        );
    }



    public void changePassword(String identifier, ChangePasswordRequest request) {
        Optional<MyUser> userOpt = repository.findByUsername(identifier);
        if (userOpt.isEmpty()) {
            userOpt = repository.findByEmail(identifier);
        }
        MyUser user = userOpt.orElseThrow(() -> new UsernameNotFoundException("User not found with identifier: " + identifier));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new IllegalArgumentException("New password and confirm password do not match");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        repository.save(user);
    }

}
