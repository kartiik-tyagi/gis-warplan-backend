package com.gis.warplan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.PublishJobItem;

public interface PublishJobItemRepository extends JpaRepository<PublishJobItem, Long> {

    List<PublishJobItem> findByJobIdOrderBySortOrderAscIdAsc(Long jobId);

    List<PublishJobItem> findByJobIdAndStatusOrderBySortOrderAscIdAsc(Long jobId, String status);

    long countByJobIdAndStatus(Long jobId, String status);
}
