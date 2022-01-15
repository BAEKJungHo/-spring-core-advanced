package hello.proxy.common.advice;

import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

// Advice 는 프록시에 적용하는 부가 기능 로직
@Slf4j
public class TimeAdvice implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        log.info("TimeProxy 실행");
        long startTime = System.currentTimeMillis();

        /*
            JdkDynamicProxyTest 의 TimeInvocationHandler 에서는 target 이 필요했는데 Advice 를 사용하는 경우에는 필요 없다.
            ProxyFactory 를 생성할때, 생성자로 target 을 미리 넣어주기 때문이다.
            MethodInvocation 내부에 target 이 있다.
         */
        Object result = invocation.proceed();

        long endTime = System.currentTimeMillis();
        long resultTime = endTime - startTime;
        log.info("TimeProxy 종료 resultTime={}", resultTime);
        return result;
    }
}
