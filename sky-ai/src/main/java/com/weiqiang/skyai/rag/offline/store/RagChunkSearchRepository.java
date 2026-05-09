package com.weiqiang.skyai.rag.offline.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RagChunkSearchRepository extends JpaRepository<RagChunkEntity, String> {

    @Query(value = """
            select
                content as content,
                metadata_json as metadataJson,
                ts_rank(fts, tsq) as score
            from rag_chunk, websearch_to_tsquery('simple', :queryTerms) tsq
            where fts @@ tsq
            order by score desc, created_at desc
            limit :topK
            """, nativeQuery = true)
    List<RagChunkSearchProjection> searchByFts(@Param("queryTerms") String queryTerms, @Param("topK") int topK);
}
