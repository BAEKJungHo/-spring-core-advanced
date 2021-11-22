package hello.advanced.app.v0.bjh;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class OrderControllerV0BJH {

    private final OrderServiceV0BJH orderServiceV0BJH;

    @GetMapping("/v0/request/bjh")
    public String request(String itemId) {
        orderServiceV0BJH.orderItem(itemId);
        return "ok";
    }

}
