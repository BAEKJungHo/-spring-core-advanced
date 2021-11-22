package hello.advanced.app.v0.bjh;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrderServiceV0BJH {

    private final OrderRepositoryV0BJH orderRepositoryV0BJH;

    public void orderItem(String itemId) {
        orderRepositoryV0BJH.save(itemId);
    }

}
