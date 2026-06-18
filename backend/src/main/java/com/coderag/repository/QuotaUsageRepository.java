package com.coderag.repository;

import com.coderag.entity.QuotaUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface QuotaUsageRepository extends JpaRepository<QuotaUsage, Long> {

    Optional<QuotaUsage> findByUserIdAndDate(Long userId, LocalDate date);
}
