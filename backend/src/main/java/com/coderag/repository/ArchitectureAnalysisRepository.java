package com.coderag.repository;

import com.coderag.entity.ArchitectureAnalysis;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArchitectureAnalysisRepository extends JpaRepository<ArchitectureAnalysis, Long> {

    List<ArchitectureAnalysis> findByRepoIdOrderByCreatedAtDesc(Long repoId);

    Page<ArchitectureAnalysis> findByRepoIdOrderByCreatedAtDesc(Long repoId, Pageable pageable);

    Optional<ArchitectureAnalysis> findFirstByRepoIdOrderByCreatedAtDesc(Long repoId);
}
