package io.github.mawsonlakes790913.chineseoutputforge.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Question;

public interface QuestionRepository extends JpaRepository<Question, Long> {

}
