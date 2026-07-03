package com.costco.gb.service;

import com.costco.gb.dto.request.CreatePurchaseOrderReviewRequest;
import com.costco.gb.entity.*;
import com.costco.gb.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderReviewServiceTest {
    @Mock PurchaseOrderRepository orderRepository;
    @Mock PurchaseOrderReviewRepository reviewRepository;
    @Mock UserRepository userRepository;
    @InjectMocks PurchaseOrderReviewService service;

    @Test
    void fiveStarReviewAddsCreditOnce() {
        User requester = User.builder().id(1L).creditScore(100).build();
        User runner = User.builder().id(2L).creditScore(80).build();
        PurchaseRequest request = PurchaseRequest.builder().itemName("商品").build();
        PurchaseOrder order = PurchaseOrder.builder().id(20L).purchaseRequest(request)
                .requester(requester).runner(runner).status("COMPLETED").build();
        when(orderRepository.findById(20L)).thenReturn(Optional.of(order));
        when(reviewRepository.save(any())).thenAnswer(inv -> { PurchaseOrderReview r = inv.getArgument(0); r.setId(1L); return r; });
        CreatePurchaseOrderReviewRequest payload = new CreatePurchaseOrderReviewRequest();
        payload.setRating(5);

        service.create(1L, 20L, payload);

        assertEquals(81, runner.getCreditScore());
        verify(userRepository).save(runner);
    }
}
