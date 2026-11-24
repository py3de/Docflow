package com.docflow.eval;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvalRunRepository extends JpaRepository<EvalRunEntity, String> {

    Page<EvalRunEntity> findAllByOrderByStartedAtDesc(Pageable pageable);
}
