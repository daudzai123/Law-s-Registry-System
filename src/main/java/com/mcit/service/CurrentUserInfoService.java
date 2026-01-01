package com.mcit.service;

import com.mcit.entity.User;
import com.mcit.repo.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserInfoService {

    @Autowired
    private UserRepository userRepository;

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName(); // get the username from SecurityContext
            return userRepository.findByUsername(username).orElse(null);
        }
        return null;
    }

    public Long getCurrentUserId() {
        User user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    public String getCurrentUserFirstName() {
        User user = getCurrentUser();
        return user != null ? user.getFirstname() : null;
    }

    // ✅ New method to get current user's username
    public String getCurrentUserUsername() {
        User user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }

    public String getCurrentUserLastName() {
        User user = getCurrentUser();
        return user != null ? user.getLastname() : null;
    }


    public String getCurrentUserEmail() {
        User user = getCurrentUser();
        return user != null ? user.getEmail() : null;
    }
}
