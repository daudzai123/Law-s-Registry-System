package com.mcit.enums;

public enum LawType {
    OSOLNAMA("Osolnama"),
    NIZAMNAMA("Nizamnama"),
    BUSINESS_ADS("Business Ads"),
    JARIDA("Jarida"),
    MAJMOA_OF_LAW("Majmoa of Law"),
    AHKAM_AND_FRAMIN("Ahkam and Framin");

    private final String displayName;

    LawType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}