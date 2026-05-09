package com.weiqiang.skyai.rag.offline.store;

import com.weiqiang.skyai.rag.offline.model.KeywordSearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagIndexRepositoryTests {

    @Test
    void searchChunksByKeywordLikeReusesFormatterKeywordsInFallback() {
        CapturingJdbcTemplate jdbcTemplate = new CapturingJdbcTemplate();
        TsQueryFormatter tsQueryFormatter = new TsQueryFormatter(new StaticKeywordExtractionClientProvider(
                query -> List.of("袁志刚", "教育背景")
        ));
        RagIndexRepository repository = new RagIndexRepository(
                jdbcTemplate,
                null,
                null,
                tsQueryFormatter
        );

        List<KeywordSearchResult> results = repository.searchChunksByKeywordLike("袁志刚是哪个大学的", 5);

        assertEquals(List.of(), results);
        assertEquals(2, jdbcTemplate.queryCalls);
        assertTrue(jdbcTemplate.lastSql.contains("metadata_json::jsonb ->> 'title'"));
        assertArrayEquals(new Object[]{
                "%袁志刚%",
                "%袁志刚%",
                "%教育背景%",
                "%教育背景%",
                5
        }, jdbcTemplate.lastArgs);
    }

    private interface KeywordExtractor {
        List<String> extract(String query);
    }

    private static final class StaticKeywordExtractionClientProvider implements ObjectProvider<KeywordExtractionClient> {

        private final KeywordExtractionClient client;

        private StaticKeywordExtractionClientProvider(KeywordExtractor extractor) {
            this.client = (query, maxKeywords) -> extractor.extract(query);
        }

        @Override
        public KeywordExtractionClient getObject(Object... args) {
            return client;
        }

        @Override
        public KeywordExtractionClient getIfAvailable() {
            return client;
        }

        @Override
        public KeywordExtractionClient getIfUnique() {
            return client;
        }

        @Override
        public void ifAvailable(java.util.function.Consumer<KeywordExtractionClient> dependencyConsumer) {
            dependencyConsumer.accept(client);
        }

        @Override
        public void ifUnique(java.util.function.Consumer<KeywordExtractionClient> dependencyConsumer) {
            dependencyConsumer.accept(client);
        }

        @Override
        public Stream<KeywordExtractionClient> stream() {
            return Stream.of(client);
        }

        @Override
        public Stream<KeywordExtractionClient> orderedStream() {
            return Stream.of(client);
        }

        @Override
        public Iterator<KeywordExtractionClient> iterator() {
            return List.of(client).iterator();
        }
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {

        private int queryCalls;
        private String lastSql = "";
        private Object[] lastArgs = new Object[0];

        private CapturingJdbcTemplate() {
            super(new EmptyDataSource());
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            queryCalls++;
            if (queryCalls == 1) {
                return List.of();
            }
            lastSql = sql;
            lastArgs = args;
            return List.of();
        }
    }

    private static final class EmptyDataSource implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getGlobal();
        }

        @Override
        public <T> T unwrap(Class<T> iface) {
            throw new UnsupportedOperationException("Not used in this test");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}
