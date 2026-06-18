package com.coderag.service;

import com.coderag.common.constant.RoleConstant;
import com.coderag.entity.AsyncTask;
import com.coderag.entity.User;
import com.coderag.exception.BusinessException;
import com.coderag.repository.AsyncTaskRepository;
import com.coderag.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理员服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final AsyncTaskRepository asyncTaskRepository;

    /**
     * 获取所有用户列表
     */
    public Page<User> listUsers(int page, int size) {
        return userRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /**
     * 修改用户角色
     */
    public User updateUserRole(Long userId, String newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        if (!newRole.equals(RoleConstant.USER) && !newRole.equals(RoleConstant.PREMIUM) && !newRole.equals(RoleConstant.ADMIN)) {
            throw new BusinessException("无效的角色: " + newRole);
        }

        user.setRole(newRole);
        return userRepository.save(user);
    }

    /**
     * 封禁/解封用户
     */
    public User setUserBanned(Long userId, boolean banned) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        // 不允许封禁自己
        if ("ADMIN".equals(user.getRole()) && banned) {
            throw new BusinessException("不能封禁管理员账号");
        }
        user.setBanned(banned);
        log.info("管理员操作：用户 {} (ID={}) 被{}封禁", user.getUsername(), userId, banned ? "" : "解");
        return userRepository.save(user);
    }

    /**
     * 设置用户自定义配额
     * @param importLimit 每日导入上限（null 表示恢复角色默认）
     * @param chatLimit 每日问答上限（null 表示恢复角色默认）
     */
    public User setUserQuota(Long userId, Integer importLimit, Integer chatLimit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        if (importLimit != null && importLimit < 0) {
            throw new BusinessException("导入配额不能为负数");
        }
        if (chatLimit != null && chatLimit < 0) {
            throw new BusinessException("问答配额不能为负数");
        }

        user.setCustomImportLimit(importLimit);
        user.setCustomChatLimit(chatLimit);
        log.info("管理员操作：用户 {} (ID={}) 配额设置为 import={}, chat={}",
                user.getUsername(), userId, importLimit, chatLimit);
        return userRepository.save(user);
    }

    /**
     * 查看全站异步任务队列（支持按 userId 和 status 筛选）
     */
    public Page<AsyncTask> listAllTasks(int page, int size, Long userId, String status) {
        Specification<AsyncTask> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return asyncTaskRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /**
     * 处理异常任务 - 标记为失败
     */
    public AsyncTask markTaskFailed(Long taskId, String reason) {
        AsyncTask task = asyncTaskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException("任务不存在"));
        task.setStatus("FAILED");
        task.setErrorMessage(reason);
        return asyncTaskRepository.save(task);
    }
}
