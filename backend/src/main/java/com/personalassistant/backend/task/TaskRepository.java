package com.personalassistant.backend.task;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByDateOrderByStartTimeAsc(LocalDate date);

    List<Task> findAllByOrderByDateAscStartTimeAsc();
}
