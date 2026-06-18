package com.coderag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 登录身份通道: USER / PREMIUM / ADMIN，用于校验身份与通道是否匹配 */
    private String role;
}
