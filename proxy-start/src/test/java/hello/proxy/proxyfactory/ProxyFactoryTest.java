package hello.proxy.proxyfactory;

import hello.proxy.common.advice.TimeAdvice;
import hello.proxy.common.service.ConcreteService;
import hello.proxy.common.service.ServiceImpl;
import hello.proxy.common.service.ServiceInterface;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
class ProxyFactoryTest {

    @DisplayName("인터페이스가 있으면 JDK 동적 프록시 사용")
    @Test
    void interfaceProxy() throws Exception {
        ServiceInterface target = new ServiceImpl();

        // ProxyFactory 를 생성할 때, 생성자에 프록시의 호출 대상을 넘겨준다.
        // 만약, 넘긴 인스턴스에 인터페이스가 존재하면 JDK 동적 프록시를 사용하고, 인터페이스가 없고 구체 클래스만 있으면 CGlib 을 통해서 동적 프록시를 생성한다.
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvice(new TimeAdvice());
        ServiceInterface proxy = (ServiceInterface) proxyFactory.getProxy();

        // class hello.proxy.common.service.ServiceImpl
        log.info("targetClass = {}", target.getClass());

        // class com.sun.proxy.$Proxy10
        log.info("proxyClass = {}", proxy.getClass());

        // 동작
        proxy.save();

        // ProxyFactory 로 만든 proxy 는 AOP 프록시이다.
        assertThat(AopUtils.isAopProxy(proxy)).isTrue();

        // 위 코드에서 Interface(ServiceInterface) 를 사용하기 때문에 JdkDynamicProxy 이다.
        assertThat(AopUtils.isJdkDynamicProxy(proxy)).isTrue();

        assertThat(AopUtils.isCglibProxy(proxy)).isFalse();
   }

    @DisplayName("구체 클래스만 있으면 CGLib 사용")
    @Test
    void concreteProxy() throws Exception {
        ConcreteService target = new ConcreteService();

        // ProxyFactory 를 생성할 때, 생성자에 프록시의 호출 대상을 넘겨준다.
        // 만약, 넘긴 인스턴스에 인터페이스가 존재하면 JDK 동적 프록시를 사용하고, 인터페이스가 없고 구체 클래스만 있으면 CGlib 을 통해서 동적 프록시를 생성한다.
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.addAdvice(new TimeAdvice());
        ConcreteService proxy = (ConcreteService) proxyFactory.getProxy();

        // class hello.proxy.common.service.ConcreteService
        log.info("targetClass = {}", target.getClass());

        // class hello.proxy.common.service.ConcreteService$$EnhancerBySpringCGLIB$$eac002dd
        log.info("proxyClass = {}", proxy.getClass());

        proxy.call();

        assertThat(AopUtils.isAopProxy(proxy)).isTrue();
        assertThat(AopUtils.isJdkDynamicProxy(proxy)).isFalse();
        assertThat(AopUtils.isCglibProxy(proxy)).isTrue();
    }

    @DisplayName("ProxyTargetClass 옵션을 사용하면 인터페이스가 있어도 CGLIB 을 사용하고, 클래스 기반 프록시 사용")
    @Test
    void proxyTargetClass() throws Exception {
        ServiceInterface target = new ServiceImpl();

        // ProxyFactory 를 생성할 때, 생성자에 프록시의 호출 대상을 넘겨준다.
        // 만약, 넘긴 인스턴스에 인터페이스가 존재하면 JDK 동적 프록시를 사용하고, 인터페이스가 없고 구체 클래스만 있으면 CGlib 을 통해서 동적 프록시를 생성한다.
        ProxyFactory proxyFactory = new ProxyFactory(target);

        // ProxyTargetClass 옵션을 사용
        proxyFactory.setProxyTargetClass(true);

        proxyFactory.addAdvice(new TimeAdvice());
        ServiceInterface proxy = (ServiceInterface) proxyFactory.getProxy();

        log.info("targetClass = {}", target.getClass());

        // class hello.proxy.common.service.ServiceImpl$$EnhancerBySpringCGLIB$$24c6991
        log.info("proxyClass = {}", proxy.getClass());

        proxy.save();

        assertThat(AopUtils.isAopProxy(proxy)).isTrue();
        assertThat(AopUtils.isJdkDynamicProxy(proxy)).isFalse();
        assertThat(AopUtils.isCglibProxy(proxy)).isTrue();
    }
}
