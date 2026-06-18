package com.coderag.repository;

import com.coderag.entity.CodeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CodeRepositoryRepository extends JpaRepository<CodeRepository, Long> {

    Page<CodeRepository> findByUserId(Long userId, Pageable pageable);

    long countByUserId(Long userId);
}
