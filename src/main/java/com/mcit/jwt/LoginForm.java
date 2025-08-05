package com.mcit.jwt;

//the below is a pojo class, but with record it become brief, immutable and auto-generated methods
public record LoginForm(String identifier, String password) { }