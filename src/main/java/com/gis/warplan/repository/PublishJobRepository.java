package com.gis.warplan.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gis.warplan.domain.PublishJob;

public interface PublishJobRepository extends JpaRepository<PublishJob, Long> {

    List<PublishJob> findTop50ByOrderByIdDesc();

    List<PublishJob> findByStatusInOrderByIdAsc(List<String> statuses);
}
