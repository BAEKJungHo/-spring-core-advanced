package hello.advanced.trace.logtrace;

import hello.advanced.trace.TraceStatus;

/**
 * LogTrace 에는 로그 추적기를 위한 최소한의 기능인
 * begin, end, exception 만 정의
 */
public interface LogTrace {

    TraceStatus begin(String message);

    void end(TraceStatus status);

    void exception(TraceStatus status, Exception e);
}
