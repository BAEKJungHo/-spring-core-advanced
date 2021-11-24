# 스프링 핵심 원리 고급편

## [로그 추적기](https://github.com/BAEKJungHo/spring-core-advanced/tree/main/advanced/src/main/java/hello/advanced)

여러분이 새로운 회사에 입사했는데, 수 년간 운영중인 거대한 프로젝트에 투입되었다. 전체 소스 코드는 수
십만 라인이고, 클래스 수도 수 백개 이상이다.
여러분에게 처음 맡겨진 요구사항은 로그 추적기를 만드는 것이다.
애플리케이션이 커지면서 점점 모니터링과 운영이 중요해지는 단계이다. 특히 최근 자주 병목이 발생하고
있다. 어떤 부분에서 병목이 발생하는지, 그리고 어떤 부분에서 예외가 발생하는지를 로그를 통해 확인하는
것이 점점 중요해지고 있다.
기존에는 개발자가 문제가 발생한 다음에 관련 부분을 어렵게 찾아서 로그를 하나하나 직접 만들어서
남겼다. 로그를 미리 남겨둔다면 이런 부분을 손쉽게 찾을 수 있을 것이다. 이 부분을 개선하고 자동화 하는
것이 여러분의 미션이다.

> 모니터링 툴을 도입하면 많은 부분이 해결되지만, 지금은 학습이 목적이라는 사실을 기억하자.

- __요구사항__
  - 모든 public 메서드의 호출과 응답 정보를 로그로 출력
  - 애플리케이션의 흐름을 변경하면 안됨
    - 로그를 남긴다고 해서 비지니스 로직의 동작에 영향을 주면 안됨
  - 메서드 호출에 걸린 시간
  - 정상 흐름과 예외 흐름 구분
    - 예외 발생 시 예외 정보가 남아야 함
  - 메서드 호출의 깊이 표현
  - HTTP 요청을 구분
    - HTTP 요청 단위로 특정 ID 를 남겨서 어떤 HTTP 요청에서 시작된 것인지 명확하게 구분이 가능해야 함
    - 트랜잭션 ID (DB 트랜잭션X), 여기서는 하나의 HTTP 요청이 시작해서 끝날 때 까지를 하나의 트랜잭션이라 함

- __예시__

```
정상 요청
[796bccd9] OrderController.request()
[796bccd9] |-->OrderService.orderItem()
[796bccd9] | |-->OrderRepository.save()
[796bccd9] | |<--OrderRepository.save() time=1004ms
[796bccd9] |<--OrderService.orderItem() time=1014ms
[796bccd9] OrderController.request() time=1016ms

예외 발생
[b7119f27] OrderController.request()
[b7119f27] |-->OrderService.orderItem()
[b7119f27] | |-->OrderRepository.save()
[b7119f27] | |<X-OrderRepository.save() time=0ms 
ex=java.lang.IllegalStateException: 예외 발생!
[b7119f27] |<X-OrderService.orderItem() time=10ms 
ex=java.lang.IllegalStateException: 예외 발생!
[b7119f27] OrderController.request() time=11ms 
ex=java.lang.IllegalStateException: 예외 발생!
```

트랜잭션 ID 와 깊이를 표현하는 방법은 기존 정보를 이어 받아야 하기 때문에 단순히 로그만 남긴다고 해결할 수 있는 것은 아니다.

## 동시성 문제

__동시성 문제란 여러 쓰레드가 동시에 같은 인스턴스의 필드 값을 변경하면서 발생하는 문제를 의미한다.__

v1, v2 프로젝트에서는 traceId 를 컨틀롤러에서 서비스로 넘겨 계산을 했다. 파라미터로 동기화하는 방식은 너무 불편하다. 따라서 FieldLogTrace 라는 클래스를 만들어
스프링 빈으로 직접 등록해서 사용한다.(스프링 빈은 싱글톤으로 등록된다.)

```java
/**
 * 파라미터로 traceId 를 넘겨서 service 에서는 nextId 를 구하여 사용하는 것을 개선하기 위해 만든 클래스
 */
@Slf4j
public class FieldLogTrace implements LogTrace {

    private static final String START_PREFIX = "-->";
    private static final String COMPLETE_PREFIX = "<--";
    private static final String EX_PREFIX = "<X-";

    private TraceId traceIdHolder; // traceId 동기화, 동시성 이슈 발생

    @Override
    public TraceStatus begin(String message) {
        syncTraceId();
        TraceId traceId = traceIdHolder;
        Long startTimeMs = System.currentTimeMillis();
        log.info("[{}] {}{}", traceId.getId(), addSpace(START_PREFIX, traceId.getLevel()), message);
        return new TraceStatus(traceId, startTimeMs, message);
    }

    private void syncTraceId() {
        if (traceIdHolder == null) {
            traceIdHolder = new TraceId();
        } else {
            traceIdHolder = traceIdHolder.createNextId();
        }
    }

    @Override
    public void end(TraceStatus status) {
        complete(status, null);

    }

    @Override
    public void exception(TraceStatus status, Exception e) {
        complete(status, e);
    }

    private void complete(TraceStatus status, Exception e) {
        Long stopTimeMs = System.currentTimeMillis();
        long resultTimeMs = stopTimeMs - status.getStartTimeMs();
        TraceId traceId = status.getTraceId();
        if (e == null) {
            log.info("[{}] {}{} time={}ms", traceId.getId(), addSpace(COMPLETE_PREFIX, traceId.getLevel()), status.getMessage(), resultTimeMs);
        } else {
            log.info("[{}] {}{} time={}ms ex={}", traceId.getId(), addSpace(EX_PREFIX, traceId.getLevel()), status.getMessage(), resultTimeMs, e.toString());
        }

        releaseTraceId();
    }

    /**
     * 메서드를 추가로 호출할 때에는 level 이 하나 증가하지만,
     * 메서드 호출이 끝나면 level 이 하나 감소해야 한다.
     */
    private void releaseTraceId() {
        if (traceIdHolder.isFirstLevel()) { // 최초 호출
            traceIdHolder = null; // destroy
        } else {
            traceIdHolder = traceIdHolder.createPreviousId();
        }
    }

    private static String addSpace(String prefix, int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append((i == level - 1) ? "|" + prefix : "|   ");
        }
        return sb.toString();
    }

}
```

싱글톤 빈으로 등록된 클래스를 사용할 때, `상태 값`이 유지가 되지 않게 설계해야 한다. 위 FieldLogTrace 를 연속으로 호출하면 잘못된 결과가 출력된다.

- 동시성 문제 발생 테스트

```java
@Slf4j
public class FieldServiceTest {

    private FieldService fieldService = new FieldService();

    @Test
    void field() {
        log.info("main start");
        // 스레드 2개 생성
        Runnable userA = () -> {
            fieldService.logic("userA");
        };
        Runnable userB = () -> {
            fieldService.logic("userB");
        };

        Thread threadA = new Thread(userA);
        threadA.setName("thread-A");
        Thread threadB = new Thread(userB);
        threadA.setName("thread-B");

        threadA.start();
        // sleep(2000); // 동시성 문제 발생 X
        sleep(100); // 동시성 문제 발생 O
        threadB.start();

        sleep(3000); // 메인 쓰레드 종료 대기
        log.info("main exit");
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```

결과적으로 Thread-A 입장에서는 저장한 데이터와 조회한 데이터가 다른 문제가 발생한다. __이처럼 여러 쓰레드가 동시에 같은 인스턴스의 필드 값을 변경하면서 발생하는 문제를 동시성 문제라 한다.__
이런 동시성 문제는 여러 쓰레드가 같은 인스턴스의 필드에 접근해야 하기 때문에 트래픽이 적은 상황에서는 확률상 잘 나타나지 않고, 트래픽이 점점 많아질 수 록 자주 발생한다.
특히 스프링 빈 처럼 싱글톤 객체의 필드를 변경하며 사용할 때 이러한 동시성 문제를 조심해야 한다.

이런 동시성 문제는 지역 변수에서는 발생하지 않는다. 지역 변수는 `쓰레드마다 각각 다른 메모리 영역이 할당`된다. 이 부분은 [프로세스와 스레드의 차이점](https://github.com/BAEKJungHo/tech-interview-study/blob/main/WEB/06.%20%ED%94%84%EB%A1%9C%EC%84%B8%EC%8A%A4%EC%99%80%20%EC%8A%A4%EB%A0%88%EB%93%9C.md)과 서로 공유하는 메모리 영역, 공유하지 않는 메모리 영역에 대해서 알면 이해할 수 있다. 결과만 말하면 쓰레드는 고유한 Stack 영역을 할당 받으며 이 영역은 다른 스레드와 공유되지 않는다. Stack 영역에는 매개변수와, 지역변수 등이 저장된다.

> 동시성 문제가 발생하는 곳은 같은 인스턴스의 필드(주로 싱글톤에서 자주 발생), 또는 static 같은 공용 필드에 접근할 때 발생한다.
>
> 동시성 문제는 값을 읽기만 하면 발생하지 않는다. 어디선가 값을 변경하기 때문에 발생한다.

## ThreadLocal

쓰레드 로컬은 해당 쓰레드만 접근할 수 있는 특별한 저장소를 말한다. 쉽게 이야기해서 물건 보관 창구를
떠올리면 된다. 여러 사람이 같은 물건 보관 창구를 사용하더라도 창구 직원은 사용자를 인식해서
사용자별로 확실하게 물건을 구분해준다.

쓰레드 로컬을 사용하면 각 쓰레드마다 `별도의 내부 저장소`를 제공한다. 따라서 같은 인스턴스의 쓰레드 로컬 필드에 접근해도 문제 없다. thread-A 가 userA 라는 값을 저장하면 쓰레드 로컬은 thread-A 전용 보관소에 데이터를 안전하게 보관한다.

![IMAGES](/images/threadlocal.JPG)

쓰레드 로컬을 통해서 데이터를 조회할 때도 thread-A 가 조회하면 쓰레드 로컬은 thread-A 전용
보관소에서 userA 데이터를 반환해준다. 물론 thread-B 가 조회하면 thread-B 전용 보관소에서
userB 데이터를 반환해준다.

자바는 언어차원에서 쓰레드 로컬을 지원하기 위한 java.lang.ThreadLocal 클래스를 제공한다.
