package com.kensai.sandbox.lexio.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class UserProfileService {

    public enum CefrLevel {
        A1(1), A2(2), B1(3), B2(4), C1(5), C2(6);

        private final int order;

        CefrLevel(int order) {
            this.order = order;
        }

        public int getOrder() {
            return order;
        }

        public boolean isLowerThan(CefrLevel other) {
            return this.order < other.order;
        }

        public static CefrLevel fromString(String level) {
            if (level == null) return A1;
            try {
                return CefrLevel.valueOf(level.toUpperCase().trim());
            } catch (IllegalArgumentException e) {
                return A1;
            }
        }
    }

    private static final String DEFAULT_SESSION = "default";
    private final Map<String, CefrLevel> userLevels = new ConcurrentHashMap<>();

    public CefrLevel getLevel(String sessionId) {
        String id = sessionId != null ? sessionId : DEFAULT_SESSION;
        return userLevels.getOrDefault(id, CefrLevel.A1);
    }

    public CefrLevel getLevel() {
        return getLevel(DEFAULT_SESSION);
    }

    public void setLevel(String sessionId, CefrLevel level) {
        String id = sessionId != null ? sessionId : DEFAULT_SESSION;
        userLevels.put(id, level);
        log.info("User level set to {} for session {}", level, id);
    }

    public void setLevel(CefrLevel level) {
        setLevel(DEFAULT_SESSION, level);
    }

    public void setLevel(String levelString) {
        setLevel(DEFAULT_SESSION, CefrLevel.fromString(levelString));
    }
}
