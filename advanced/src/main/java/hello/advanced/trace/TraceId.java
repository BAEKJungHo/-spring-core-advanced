package hello.advanced.trace;

import java.util.UUID;

/**
 * 트랜잭션 ID 와 깊이를 표현 하는 level 을 묶어서 TraceId 로 표현
 */
public class TraceId {

    private String id; // [796bccd9]
    private int level; // 깊이

    public TraceId() {
        this.id = createId();
        this.level = 0;
    }

    private TraceId(String id, int level) {
        this.id = id;
        this.level = level;
    }

    private String createId() {
        // 생성된 UUID ab99e16f-3cde-4d24-8241-256108c203a2 중 앞 8자리만 사용
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public TraceId createNextId() {
        return new TraceId(id, level + 1);
    }

    public TraceId createPreviousId() {
        return new TraceId(id, level - 1);
    }

    public boolean isFirstLevel() {
        return level == 0;
    }

    public String getId() {
        return id;
    }

    public int getLevel() {
        return level;
    }
}
