package com.coderag.service;

import com.coderag.common.constant.RoleConstant;
import com.coderag.config.security.JwtUtils;
import com.coderag.dto.LoginRequest;
import com.coderag.dto.RegisterRequest;
import com.coderag.entity.User;
import com.coderag.exception.BusinessException;
import com.coderag.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    public Map<String, Object> register(RegisterRequest request) {
        log.info("注册请求: username={}, email={}", request.getUsername(), request.getEmail());
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("邮箱已被注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));

        user.setRole(RoleConstant.USER);

        try {
            log.info("开始保存用户到数据库...");
            user = userRepository.save(user);
            log.info("用户保存成功: id={}, username={}", user.getId(), user.getUsername());
        } catch (Exception e) {
            log.error("用户保存失败! username={}, 异常类型: {}, 错误: {}",
                    request.getUsername(), e.getClass().getName(), e.getMessage(), e);
            throw e;
        }
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        return result;
    }

    public Map<String, Object> login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("用户名或密码错误"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }

        // 校验登录身份通道与实际角色是否匹配
        String requestedRole = request.getRole();
        if (requestedRole != null && !requestedRole.isBlank() && !user.getRole().equals(requestedRole)) {
            String actualLabel = switch (user.getRole()) {
                case "ADMIN" -> "管理员";
                case "PREMIUM" -> "高级用户";
                default -> "普通用户";
            };
            throw new BusinessException("该账号为" + actualLabel + "，请选择正确的身份通道登录");
        }

        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), user.getRole());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("role", user.getRole());
        return result;
    }
}
