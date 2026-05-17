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
                c.content as content,
                c.metadata_json as metadataJson,
                ts_rank(c.fts, tsq) as score
            from rag_chunk c
            join rag_document d on d.document_id = c.document_id
            cross join websearch_to_tsquery('simple', :queryTerms) tsq
            where d.active = true
              and c.fts @@ tsq
            order by score desc, c.created_at desc
            limit :topK
            """, nativeQuery = true)
    List<RagChunkSearchProjection> searchByFts(@Param("queryTerms") String queryTerms, @Param("topK") int topK);
}
