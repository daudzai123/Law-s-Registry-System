package com.mcit.service;

import com.mcit.entity.MyUser;
import com.mcit.repo.MyUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserInfoService {

    @Autowired
    private MyUserRepository userRepository;

    public MyUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName(); // get the username from SecurityContext
            return userRepository.findByUsername(username).orElse(null);
        }
        return null;
    }

    public Long getCurrentUserId() {
        MyUser user = getCurrentUser();
        return user != null ? user.getId() : null;
    }

    public String getCurrentUserFirstName() {
        MyUser user = getCurrentUser();
        return user != null ? user.getFirstname() : null;
    }

    // ✅ New method to get current user's username
    public String getCurrentUserUsername() {
        MyUser user = getCurrentUser();
        return user != null ? user.getUsername() : null;
    }

    public String getCurrentUserLastName() {
        MyUser user = getCurrentUser();
        return user != null ? user.getLastname() : null;
    }


    public String getCurrentUserEmail() {
        MyUser user = getCurrentUser();
        return user != null ? user.getEmail() : null;
    }
}
