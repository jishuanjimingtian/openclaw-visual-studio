package com.openclaw.vs.repository;

import com.openclaw.vs.model.DeploymentTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeploymentTaskRepository extends JpaRepository<DeploymentTask, String> {

    List<DeploymentTask> findByStatusOrderByStartTimeDesc(String status);

    List<DeploymentTask> findAllByOrderByStartTimeDesc();

    Page<DeploymentTask> findAllByOrderByStartTimeDesc(Pageable pageable);
}