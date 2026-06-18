package com.coderag.repository;

import com.coderag.entity.AsyncTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AsyncTaskRepository extends JpaRepository<AsyncTask, Long>, JpaSpecificationExecutor<AsyncTask> {

    List<AsyncTask> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<AsyncTask> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(String status);

    List<AsyncTask> findByStatusAndCreatedAtBefore(String status, LocalDateTime threshold);
}
