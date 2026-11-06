package com.memento3d.customization;
import com.memento3d.customization.application.service.*;
import com.memento3d.orders.application.service.impl.OrderServiceImpl;
import com.memento3d.orders.infrastructure.persistence.repository.*;
import com.memento3d.orders.api.request.CreateOrderRequest;
import com.memento3d.orders.domain.model.*;
import com.memento3d.cart.domain.model.*;
import com.memento3d.cart.infrastructure.persistence.repository.CartRepository;
import com.memento3d.payment.infrastructure.persistence.repository.PaymentTransactionRepository;
import com.memento3d.identity.domain.model.Role;
import com.memento3d.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomPrintCheckoutTest {
    final OrderRepository orders=mock(OrderRepository.class);
    final CartRepository carts=mock(CartRepository.class);
    final ModelCustomizationService models=mock(ModelCustomizationService.class);
    final PrintDesignService designs=mock(PrintDesignService.class);
    final OrderServiceImpl service=new OrderServiceImpl(orders,null,carts,null,null,mock(PaymentTransactionRepository.class),null,null,null,null,null,models,designs);
    final UUID user=UUID.randomUUID(),output=UUID.randomUUID(),fit=UUID.randomUUID(),scan=UUID.randomUUID();
    CreateOrderRequest request(){return CreateOrderRequest.builder().modelOutputId(output).fitResultId(fit).customizationRevision(3L)
        .requestedHeightCm(12).idempotencyKey("custom:"+UUID.randomUUID()).recipientName("Customer").phone("0901234567").shippingAddress("Test address").build();}
    @Test void customCheckoutFreezesSavedSelectionWithoutConsumingCartOrTrustingClientPrice() {
        var cart=mock(Cart.class);when(cart.getItems()).thenReturn(List.of(mock(CartItem.class)));when(carts.findWithItemsByCustomerId(user)).thenReturn(Optional.of(cart));
        var snapshot=new FrozenDesign(output,fit,user,scan,UUID.randomUUID(),UUID.randomUUID(),3,"mvp-v1","graduation",Map.of("headwear",UUID.randomUUID()),null,List.of());
        when(models.freeze(output,fit,3L,user)).thenReturn(snapshot);when(designs.json(any())).thenReturn("{}");
        when(orders.saveAndFlush(any())).thenAnswer(call->{Order order=call.getArgument(0);order.setId(UUID.randomUUID());order.getItems().forEach(item->item.setId(UUID.randomUUID()));return order;});
        var result=service.createOrder(request(),user);
        assertEquals(BigDecimal.ZERO,result.getTotalPrice());assertEquals(scan,result.getScanId());
        verify(designs).attach(any(),any(),same(snapshot),eq(12));verify(cart,never()).clearItems();verify(carts,never()).save(any());
        var tampered=request();tampered.setTotalPrice(BigDecimal.valueOf(1));
        assertThrows(BusinessException.class,()->service.createOrder(tampered,user));
    }
    @Test void duplicateKeyCannotReturnAnotherCustomersOrder() {
        var request=request();var other=Order.builder().id(UUID.randomUUID()).userId(UUID.randomUUID()).build();
        when(orders.findByUserIdAndIdempotencyKey(user,request.getIdempotencyKey())).thenReturn(Optional.of(other));
        assertThrows(RuntimeException.class,()->service.createOrder(request,user));verify(models,never()).freeze(any(),any(),any(),any());
    }
    @Test void customOrderCannotPayOrEnterProductionBeforeQuoteAcceptanceAndPayment() {
        UUID id=UUID.randomUUID();var order=Order.builder().id(id).userId(user).scanId(scan).status(OrderStatus.PENDING).totalPrice(BigDecimal.ZERO).build();
        when(orders.findById(id)).thenReturn(Optional.of(order));when(designs.isCustom(id)).thenReturn(true);
        assertThrows(BusinessException.class,()->service.validateOrderForPayment(id,user));
        assertThrows(BusinessException.class,()->service.updateOrderStatus(id,OrderStatus.CONFIRMED,UUID.randomUUID(),Role.ROLE_MANAGER));
        verify(orders,never()).save(any());
    }
}
