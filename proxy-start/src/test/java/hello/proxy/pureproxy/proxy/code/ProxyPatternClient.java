package hello.proxy.pureproxy.proxy.code;

public class ProxyPatternClient {

    /**
     * Subject 인터페이스만 의존하고 있기 때문에 프록시가 주입된건지, 실제 객체가 주입된건지 알 수 없다.
     */
    private Subject subject;

    public ProxyPatternClient(Subject subject) {
        this.subject = subject;
    }

    public void execute() {
        subject.operation();
    }
}
