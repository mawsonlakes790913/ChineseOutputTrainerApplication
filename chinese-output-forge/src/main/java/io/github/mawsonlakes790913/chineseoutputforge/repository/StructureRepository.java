package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.github.mawsonlakes790913.chineseoutputforge.entity.Structure;

public interface StructureRepository
        extends JpaRepository<Structure, Long> {

	@Query(value = """
		    SELECT DISTINCT structure_id
		    FROM structure
		    ORDER BY structure_id
		    """,
		    nativeQuery = true)
		List<Long> findAllStructureIds();
}
