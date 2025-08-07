package com.mcit.enums;

public enum Role {
    ROLE_LAW_MANAGER("LawManger"),
    ROLE_ADMIN("Admin");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
