package com.supercoding.shoppingmallbackend.repository;

import com.supercoding.shoppingmallbackend.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question,Long> {
}
