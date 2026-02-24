package com.pgms.dto;

import jakarta.validation.constraints.NotBlank;

public class AccountRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String mode;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
