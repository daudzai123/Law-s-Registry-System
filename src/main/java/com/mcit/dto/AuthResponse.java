package com.mcit.dto;

import java.util.List;

public record AuthResponse(String token, List<String> roles) {}
