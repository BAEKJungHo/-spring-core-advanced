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

싱글톤 빈에서 상태값을 유지해야하는 경우에 동시성 이슈를 해결하기 위해서 `ThreadLocal`을 지원한다.

> 주의
> 
> 해당 쓰레드가 쓰레드 로컬을 모두 사용하고 나면 ThreadLocal.remove() 를 호출해서 쓰레드 로컬에 저장된 값을 제거해주어야 한다. 제거하지 않으면 특정 상황에서 메모리 누수, 개인정보 등의 문제가 발생할 수 있다.

쓰레드 로컬은 해당 쓰레드만 접근할 수 있는 특별한 저장소를 말한다. 쉽게 이야기해서 물건 보관 창구를
떠올리면 된다. 여러 사람이 같은 물건 보관 창구를 사용하더라도 창구 직원은 사용자를 인식해서
사용자별로 확실하게 물건을 구분해준다.

쓰레드 로컬을 사용하면 각 쓰레드마다 `별도의 내부 저장소`를 제공한다. 따라서 같은 인스턴스의 쓰레드 로컬 필드에 접근해도 문제 없다. thread-A 가 userA 라는 값을 저장하면 쓰레드 로컬은 thread-A 전용 보관소에 데이터를 안전하게 보관한다.

![IMAGES](/images/threadlocal.JPG)

쓰레드 로컬을 통해서 데이터를 조회할 때도 thread-A 가 조회하면 쓰레드 로컬은 thread-A 전용
보관소에서 userA 데이터를 반환해준다. 물론 thread-B 가 조회하면 thread-B 전용 보관소에서
userB 데이터를 반환해준다.

자바는 언어차원에서 쓰레드 로컬을 지원하기 위한 java.lang.ThreadLocal 클래스를 제공한다.

```java
@Slf4j
public class ThreadLocalService {

    // 기존 전역 변수를 ThreadLocal 로 변경했다.
    private ThreadLocal<String> nameStore = new ThreadLocal<>();

    public String logic(String name) {
        log.info("저장 name={} -> nameStore={}", name, nameStore.get());
        nameStore.set(name);
        sleep(1000);
        log.info("조회 nameStore={}", nameStore.get());
        return nameStore.get();
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

동시성 문제에서 다뤘던 FieldLogTrace 코드를 동시성 문제를 해결하기 위해 ThreadLocal 을 적용하면 다음과 같다.

```java
@Slf4j
public class ThreadLocalLogTrace implements LogTrace {

    private static final String START_PREFIX = "-->";
    private static final String COMPLETE_PREFIX = "<--";
    private static final String EX_PREFIX = "<X-";

    private ThreadLocal<TraceId> traceIdHolder = new ThreadLocal<>();

    @Override
    public TraceStatus begin(String message) {
        syncTraceId();
        TraceId traceId = traceIdHolder.get();
        Long startTimeMs = System.currentTimeMillis();
        log.info("[{}] {}{}", traceId.getId(), addSpace(START_PREFIX, traceId.getLevel()), message);
        return new TraceStatus(traceId, startTimeMs, message);
    }

    private void syncTraceId() {
        TraceId traceId = traceIdHolder.get();
        if (traceId == null) {
            traceIdHolder.set(new TraceId());
        } else {
            traceIdHolder.set(traceId.createNextId());
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

    private void releaseTraceId() {
        TraceId traceId = traceIdHolder.get();
        if (traceId.isFirstLevel()) {
            traceIdHolder.remove(); // destroy
        } else {
            traceIdHolder.set(traceId.createPreviousId());
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

### ThreadLocal 사용 시 주의할 점 !!

쓰레드 로컬의 값을 사용 후 제거하지 않고 그냥 두면 WAS(톰캣)처럼 쓰레드 풀을 사용하는 경우에 심각한 문제가 발생할 수 있다.

#### 사용자 A 저장 요청

![IMAGES](/images/tl1.JPG)

1. 사용자A가 저장 HTTP 를 요청했다.
2. WAS 는 쓰레드 풀에서 쓰레드를 하나 조회한다.
3. 쓰레드 thread-A 가 할당되었다.
4. thread-A 는 사용자A 의 데이터를 쓰레드 로컬에 저장한다.
5. 쓰레드 로컬의 thread-A 전용 보관소에 사용자A 데이터를 보관한다

#### 사용자A 저장 요청 종료

![IMAGES](/images/tl2.JPG)

1. 사용자A의 HTTP 응답이 끝난다.
2. WAS는 사용이 끝난 thread-A 를 쓰레드 풀에 반환한다. 쓰레드를 생성하는 비용은 비싸기 때문에 쓰레드를 제거하지 않고, 보통 쓰레드 풀을 통해서 쓰레드를 재사용한다.
3. thread-A 는 쓰레드풀에 아직 살아있다. 따라서 쓰레드 로컬의 thread-A 전용 보관소에 사용자A 데이터도 함께 살아있게 된다.

#### 사용자B 조회 요청

![IMAGES](/images/tl3.JPG)

1. 사용자B가 조회를 위한 새로운 HTTP 요청을 한다.
2. WAS 는 쓰레드 풀에서 쓰레드를 하나 조회한다.
3. 쓰레드 thread-A 가 할당되었다. (물론 다른 쓰레드가 할당될 수 도 있다. 어떤 쓰레드가 할당 될 지 모른다.)
4. 이번에는 조회하는 요청이다. thread-A 는 쓰레드 로컬에서 데이터를 조회한다.
5. 쓰레드 로컬은 thread-A 전용 보관소에 있는 사용자A 값을 반환한다.
6. 결과적으로 사용자A 값이 반환된다.
7. 사용자B는 사용자A의 정보를 조회하게 된다.


결과적으로 사용자B는 사용자A의 데이터를 확인하게 되는 심각한 문제가 발생하게 된다.

이런 문제를 예방하려면 사용자A의 요청이 끝날 때 쓰레드 로컬의 값을 `ThreadLocal.remove()` 를 통해서 꼭 제거해야 한다.

쓰레드 로컬을 사용할 때는 이 부분을 꼭! 기억하자.

## 템플릿 메서드 패턴

### 핵심기능과 부가기능

- __핵심 기능__
  - 핵심 기능은 해당 객체가 제공하는 고유의 기능이다. 예를 들어서 orderService 의 핵심 기능은 주문 로직이다. 메서드 단위로 보면 orderService.orderItem() 의 핵심 기능은 주문 데이터를 저장하기 위해 리포지토리를 호출하는 orderRepository.save(itemId) 코드가 핵심 기능이다.
- __부가기능__
  - 부가 기능은 핵심 기능을 보조하기 위해 제공되는 기능이다. 예를 들어서 로그 추적 로직, 트랜잭션 기능이
있다. 이러한 부가 기능은 단독으로 사용되지는 않고, 핵심 기능과 함께 사용된다. 예를 들어서 로그 추적
기능은 어떤 핵심 기능이 호출되었는지 로그를 남기기 위해 사용한다. 그러니까 핵심 기능을 보조하기 위해
존재한다.

V0는 핵심 기능만 있지만, 로그 추적기를 추가한 V3코드는 핵심 기능과 부가 기능이 함께 섞여있다.

만약 클래스가 수백 개라면 어떻게 하겠는가 ? 이 문제를 좀 더 효율적으로 처리할 수 있는 방법이 있을까 ?

로그 추적기를 사용하는 코드를 자세히 살펴보면 다음과 같은 동일한 패턴이 있다.

```java
TraceStatus status = null;
try {
 status = trace.begin("message");
 // 핵심 기능 호출
 trace.end(status);
} catch (Exception e) {
 trace.exception(status, e);
 throw e;
}
```

부가 기능과 관련된 코드가 중복이니 중복을 별도의 메서드로 뽑아내면 될 것 같다. 그런데, try ~ catch
는 물론이고, 핵심 기능 부분이 중간에 있어서 단순하게 메서드로 추출하는 것은 어렵다.

## 변하는 것과 변하지 않는 것을 분리

좋은 설계는 변하는 것과 변하지 않는 것을 분리하는 것이다.

여기서 핵심 기능 부분은 변하고, 로그 추적기를 사용하는 부분은 변하지 않는 부분이다. 이 둘을 분리해서 모듈화해야 한다.

`템플릿 메서드 패턴(Template Method Pattern)`은 이런 문제를 해결하는 디자인 패턴이다.

![IMAGES](/images/templatemethod.JPG)

부모 클래스에 알고리즘의 골격인 템플릿을 정의하고, 일부 변경되는 로직은 자식 클래스에 정의하는
것이다. 이렇게 하면 자식 클래스가 알고리즘의 전체 구조를 변경하지 않고, 특정 부분만 재정의할 수 있다. 
결국 `상속`과 `오버라이딩`을 통한 `다형성`으로 문제를 해결하는 것이다.

## 템플릿 메서드의 단점 : 상속을 사용한다.

템플릿 메서드 패턴은 상속을 사용한다. 따라서 상속에서 오는 단점들을 그대로 안고간다. 특히 자식
클래스가 부모 클래스와 컴파일 시점에 강하게 결합되는 문제가 있다. 이것은 의존관계에 대한 문제이다. 
자식 클래스 입장에서는 부모 클래스의 기능을 전혀 사용하지 않는다.

이번 장에서 지금까지 작성했던 코드를 떠올려보자. 자식 클래스를 작성할 때 부모 클래스의 기능을 사용한
것이 있었던가?

그럼에도 불구하고 템플릿 메서드 패턴을 위해 자식 클래스는 부모 클래스를 상속 받고 있다.

상속을 받는 다는 것은 특정 부모 클래스를 의존하고 있다는 것이다. 자식 클래스의 extends 다음에 바로
부모 클래스가 코드상에 지정되어 있다. 따라서 부모 클래스의 기능을 사용하든 사용하지 않든 간에 부모
클래스를 강하게 의존하게 된다. 여기서 강하게 의존한다는 뜻은 자식 클래스의 코드에 부모 클래스의
코드가 명확하게 적혀 있다는 뜻이다. UML 에서 상속을 받으면 삼각형 화살표가 자식 -> 부모 를 향하고
있는 것은 이런 의존관계를 반영하는 것이다.

추가로 템플릿 메서드 패턴은 상속 구조를 사용하기 때문에, 별도의 클래스나 익명 내부 클래스를 만들어야
하는 부분도 복잡하다.

지금까지 설명한 이런 부분들을 더 깔끔하게 개선하려면 어떻게 해야할까?

템플릿 메서드 패턴과 비슷한 역할을 하면서 상속의 단점을 제거할 수 있는 디자인 패턴이 바로 `전략 패턴(Strategy Pattern)`이다.

### 좋은 설계란?

좋은 설계라는 것은 무엇일까? 수 많은 멋진 정의가 있겠지만, 진정한 좋은 설계는 바로 변경이 일어날 때
자연스럽게 드러난다.
지금까지 로그를 남기는 부분을 모아서 하나로 모듈화하고, 비즈니스 로직 부분을 분리했다. 여기서 만약
로그를 남기는 로직을 변경해야 한다고 생각해보자. 그래서 AbstractTemplate 코드를 변경해야 한다
가정해보자. 단순히 AbstractTemplate 코드만 변경하면 된다.
템플릿이 없는 V3 상태에서 로그를 남기는 로직을 변경해야 한다고 생각해보자. 이 경우 모든 클래스를 다
찾아서 고쳐야 한다. 클래스가 수백 개라면 생각만해도 끔찍하다.

### 단일 책임 원칙(SRP)

V4 는 단순히 템플릿 메서드 패턴을 적용해서 소스코드 몇줄을 줄인 것이 전부가 아니다.
로그를 남기는 부분에 단일 책임 원칙(SRP)을 지킨 것이다. 변경 지점을 하나로 모아서 변경에 쉽게 대처할
수 있는 구조를 만든 것이다.

## 전략 패턴(Strategy Pattern)

전략 패턴은 변하지 않는 부분을 `Context` 라는 곳에 두고, 변하는 부분을 `Strategy` 라는 인터페이스를
만들고 해당 인터페이스를 구현하도록 해서 문제를 해결한다. 상속이 아니라 위임으로 문제를 해결하는
것이다. 전략 패턴에서 `Context` 는 변하지 않는 템플릿 역할을 하고, `Strategy` 는 변하는 알고리즘 역할을 한다.

![IMAGES](/images/strategy.JPG)

### 필드에 전략을 보관하는 방식

```java
/**
 * 필드에 전략을 보관하는 방식
 */
@Slf4j
public class ContextV1 {

    private Strategy strategy;

    public ContextV1(Strategy strategy) {
        this.strategy = strategy;
    }

    public void execute() {
        long startTime = System.currentTimeMillis();
        //비즈니스 로직 실행
        strategy.call(); //위임
        //비즈니스 로직 종료
        long endTime = System.currentTimeMillis();
        long resultTime = endTime - startTime;
        log.info("resultTime={}", resultTime);
    }
}
```
```java
/**
 * 전략 패턴 사용
 */
@Test
void strategyV1() {
    StrategyLogic1 strategyLogic1 = new StrategyLogic1();
    ContextV1 context1 = new ContextV1(strategyLogic1);
    context1.execute();

    StrategyLogic2 strategyLogic2 = new StrategyLogic2();
    ContextV1 context2 = new ContextV1(strategyLogic2);
    context2.execute();
}
```

ContextV1 은 변하지 않는 로직을 가지고 있는 템플릿 역할을 하는 코드이다. 전략 패턴에서는 이것을
컨텍스트(문맥)이라 한다.
쉽게 이야기해서 컨텍스트(문맥)는 크게 변하지 않지만, 그 문맥 속에서 strategy 를 통해 일부 전략이
변경된다 생각하면 된다.

__전략 패턴의 핵심은 Context 는 Strategy 인터페이스에만 의존한다는 점이다. 덕분에 Strategy 의
구현체를 변경하거나 새로 만들어도 Context 코드에는 영향을 주지 않는다.__

어디서 많이 본 코드 같지 않은가? 그렇다. 바로 스프링에서 의존관계 주입에서 사용하는 방식이 바로 전략 패턴이다.

하지만 위 필드에 전략을 보관하는 방식은 `선 조립 후, 실행` 하는 경우에 적합하다.

이 방식의 단점은 Context 와 Strategy 를 조립한 이후에는 전략을 변경하기가 번거롭다는 점이다. 물론
Context 에 setter 를 제공해서 Strategy 를 넘겨 받아 변경하면 되지만, Context 를 싱글톤으로
사용할 때는 동시성 이슈 등 고려할 점이 많다.

### 전략을 파라미터로 전달 받는 방식

```java
/**
 * 전략을 파라미터로 전달 받는 방식
 */
@Slf4j
public class ContextV2 {

    public void execute(Strategy strategy) {
        long startTime = System.currentTimeMillis();
        //비즈니스 로직 실행
        strategy.call(); //위임
        //비즈니스 로직 종료
        long endTime = System.currentTimeMillis();
        long resultTime = endTime - startTime;
        log.info("resultTime={}", resultTime);
    }
}
```
```java
/**
 * 전략 패턴 적용
 */
@Test
void strategyV1() {
    ContextV2 context = new ContextV2();
    context.execute(new StrategyLogic1());
    context.execute(new StrategyLogic2());
}
```

클라이언트는 Context 를 실행하는 시점에 원하는 Strategy 를 전달할 수 있다. 따라서 이전 방식과
비교해서 원하는 전략을 더욱 유연하게 변경할 수 있다.
테스트 코드를 보면 하나의 Context 만 생성한다. 그리고 하나의 Context 에 실행 시점에 여러 전략을
인수로 전달해서 유연하게 실행하는 것을 확인할 수 있다.

## 템플릿 콜백 패턴

ContextV2 는 변하지 않는 템플릿 역할을 한다. 그리고 변하는 부분은 파라미터로 넘어온 Strategy 의
코드를 실행해서 처리한다. 이렇게 다른 코드의 인수로서 넘겨주는 실행 가능한 코드를 콜백(callback)이라
한다.

- __콜백 정의__
  - 프로그래밍에서 `콜백(callback)` 또는 `콜 애프터 함수(call-after function)`는 다른 코드의 인수로서
넘겨주는 실행 가능한 코드를 말한다. 콜백을 넘겨받는 코드는 이 콜백을 필요에 따라 즉시 실행할 수도
있고, 아니면 나중에 실행할 수도 있다. 

쉽게 이야기해서 callback 은 코드가 호출(call)은 되는데 코드를 넘겨준 곳의 뒤(back)에서 실행된다는 뜻이다.

- ContextV2 예제에서 콜백은 Strategy 이다.
- 여기에서는 클라이언트에서 직접 Strategy 를 실행하는 것이 아니라, 클라이언트가 ContextV2.execute(..) 를 실행할 때 Strategy 를 넘겨주고, ContextV2 뒤에서 Strategy 가
실행된다.

- __자바 언어에서의 콜백__
  - __매개변수로 전략 객체나, 기능을 갖고 있는 객체를 넘긴 후, 뒤에서 그 객체를 이용하여 기능을 실행하는 것을 말한다.__
  - 자바 언어에서 실행 가능한 코드를 인수로 넘기려면 객체가 필요하다. 자바8부터는 람다를 사용할 수 있다.
  - 자바 8 이전에는 보통 하나의 메소드를 가진 인터페이스를 구현하고, 주로 익명 내부 클래스를 사용했다. 최근에는 주로 람다를 사용한다.

- 스프링에서는 ContextV2 와 같은 방식의 전략 패턴을 템플릿 콜백 패턴이라 한다. 전략 패턴에서 Context 가 템플릿 역할을 하고, Strategy 부분이 콜백으로 넘어온다 생각하면 된다.

# 프록시와 데코레이터 패턴

예제는 크게 3가지 상황으로 만든다. 아래 세 가지 상황은 다 실무에서 사용되는 방식이다.

- v1 - 인터페이스와 구현 클래스 - 스프링 빈으로 수동 등록
- v2 - 인터페이스 없는 구체 클래스 - 스프링 빈으로 수동 등록
- v3 - 컴포넌트 스캔으로 스프링 빈 자동 등록

### 스프링 빈 수동 등록 - v1

```java
// 스프링 빈으로 수동 등록
@Configuration
public class AppV1Config {

    @Bean
    public OrderControllerV1 orderControllerV1() {
        return new OrderControllerV1Impl(orderServiceV1());
    }

    @Bean
    public OrderServiceV1 orderServiceV1() {
        return new OrderServiceV1Impl(orderRepositoryV1());
    }

    @Bean
    public OrderRepositoryV1 orderRepositoryV1() {
        return new OrderRepositoryV1Impl();
    }

}
```

```java
@Import(AppV1Config.class)
@SpringBootApplication(scanBasePackages = "hello.proxy.app") // 주의
public class ProxyApplication {
 public static void main(String[] args) {
    SpringApplication.run(ProxyApplication.class, args);
 }
}
```

- `@Import(AppV1Config.class)`
  - 클래스를 스프링 빈으로 등록한다. 여기서는 AppV1Config.class 를
스프링 빈으로 등록한다. 일반적으로 @Configuration 같은 설정 파일을 등록할 때 사용하지만, 스프링
빈을 등록할 때도 사용할 수 있다.
- `@SpringBootApplication(scanBasePackages = "hello.proxy.app")`
  - @ComponentScan 의 기능과 같다. 컴포넌트 스캔을 시작할 위치를 지정한다. 이 값을 설정하면 해당 패키지와 그 하위 패키지를
컴포넌트 스캔한다. 이 값을 사용하지 않으면 ProxyApplication 이 있는 패키지와 그 하위 패키지를 스캔한다. 참고로 v3 에서 지금 설정한 컴포넌트 스캔 기능을 사용한다.
