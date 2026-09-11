package io.github.mawsonlakes790913.chineseoutputforge.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
	
	@Query("""
	        SELECT s
	        FROM Structure s
	        ORDER BY s.structureId ASC
	        """)
	Page<Structure> findStructures(Pageable pageable);
}
