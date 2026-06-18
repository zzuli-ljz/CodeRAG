package com.coderag.repository;

import com.coderag.entity.CodeGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodeGraphRepository extends JpaRepository<CodeGraph, Long> {

    List<CodeGraph> findByRepoIdOrderByCreatedAtDesc(Long repoId);

    Page<CodeGraph> findByRepoIdOrderByCreatedAtDesc(Long repoId, Pageable pageable);

    Optional<CodeGraph> findFirstByRepoIdOrderByCreatedAtDesc(Long repoId);
}
