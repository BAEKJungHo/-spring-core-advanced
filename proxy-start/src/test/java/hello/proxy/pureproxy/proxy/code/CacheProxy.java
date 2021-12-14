package hello.proxy.pureproxy.proxy.code;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CacheProxy implements Subject {

    /**
     * target : 프록시가 호출하는 대상
     * 프록시가 최종적으로 실제 객체를 호출해야하기 때문에 실제 객체에 대한 참조를 가지고 있어야 한다.
     */
    private Subject target;
    private String cacheValue;

    public CacheProxy(Subject target) {
        this.target = target;
    }

    @Override
    public String operation() {
        log.info("프록시 호출");
        if(cacheValue == null) {
            cacheValue = target.operation();
        }
        return cacheValue;
    }
}
