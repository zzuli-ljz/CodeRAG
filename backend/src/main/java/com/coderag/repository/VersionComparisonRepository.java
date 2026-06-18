package com.coderag.repository;

import com.coderag.entity.VersionComparison;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VersionComparisonRepository extends JpaRepository<VersionComparison, Long> {

    Page<VersionComparison> findByUserIdAndRepoIdOrderByCreatedAtDesc(Long userId, Long repoId, Pageable pageable);
}
