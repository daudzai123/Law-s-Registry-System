package com.mcit.dto;

import com.mcit.entity.User;
import lombok.Data;

import java.util.Map;

@Data
public class UserProfileDTO {

    private final String fullName;
    private final String username;
    private final String email;
    private final String role;
    private final String profileImage;

    public UserProfileDTO(User user) {
        this.fullName = user.getFirstname() + " " + user.getLastname();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.role = String.valueOf(user.getRole());
        this.profileImage = user.getProfileImage(); // adjust if stored differently
    }

}
