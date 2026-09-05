package com.example.ratelimiter.api;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The "business logic" behind the protected endpoint: it stores the incoming string in Postgres
 * and hands back what was saved. Deliberately trivial - the interesting part of this project is
 * the limiter that sits in front of it.
 */
@Service
public class MessageService {

    private static final String INSERT_SQL = """
            INSERT INTO message (client_id, content)
            VALUES (:clientId, :content)
            RETURNING id, client_id, content, created_at
            """;

    private static final String RECENT_SQL = """
            SELECT id, client_id, content, created_at
            FROM message
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            """;

    private final JdbcClient jdbcClient;

    public MessageService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public MessageResponse save(String clientId, String content) {
        return jdbcClient.sql(INSERT_SQL)
                .param("clientId", clientId)
                .param("content", content)
                .query(MessageService::toResponse)
                .single();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> recent(int limit) {
        return jdbcClient.sql(RECENT_SQL)
                .param("limit", limit)
                .query(MessageService::toResponse)
                .list();
    }

    private static MessageResponse toResponse(ResultSet rs, int rowNum) throws SQLException {
        String content = rs.getString("content");
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new MessageResponse(
                rs.getLong("id"),
                rs.getString("client_id"),
                content,
                content.length(),
                createdAt.toInstant());
    }
}
