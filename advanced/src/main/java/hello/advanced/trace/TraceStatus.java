package hello.advanced.trace;

/**
 * 로그의 상태 정보
 */
public class TraceStatus {

    private TraceId traceId;
    private Long startTimeMs; // 로그 시작 시간, 로그 종료 시 이 시작 시간을 기준으로 시간 ~ 종료 전체 수행시간을 구할 수 있다.
    private String message;

    public TraceStatus(TraceId traceId, Long startTimeMs, String message) {
        this.traceId = traceId;
        this.startTimeMs = startTimeMs;
        this.message = message;
    }

    public TraceId getTraceId() {
        return traceId;
    }

    public Long getStartTimeMs() {
        return startTimeMs;
    }

    public String getMessage() {
        return message;
    }
}
