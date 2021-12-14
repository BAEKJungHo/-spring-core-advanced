package hello.proxy.concreteproxy;

import hello.proxy.concreteproxy.code.ConcreteClient;
import hello.proxy.concreteproxy.code.ConcreteLogic;
import hello.proxy.concreteproxy.code.TimeProxy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("구체 클래스 기반 프록시 테스트")
public class ConcreteProxyTest {

    @DisplayName("프록시 적용 X")
    @Test
    void noProxy() {
        ConcreteLogic concreteLogic = new ConcreteLogic();
        ConcreteClient client = new ConcreteClient(concreteLogic);
        client.execute();
    }

    @Test
    void addProxy() {
        ConcreteLogic concreteLogic = new ConcreteLogic();
        TimeProxy timeProxy = new TimeProxy(concreteLogic);
        ConcreteClient client = new ConcreteClient(timeProxy);
        client.execute();
    }
}
