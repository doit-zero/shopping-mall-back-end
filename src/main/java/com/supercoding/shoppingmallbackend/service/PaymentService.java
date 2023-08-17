package com.supercoding.shoppingmallbackend.service;

import com.supercoding.shoppingmallbackend.common.CommonResponse;
import com.supercoding.shoppingmallbackend.common.Error.CustomException;
import com.supercoding.shoppingmallbackend.common.Error.domain.*;
import com.supercoding.shoppingmallbackend.common.util.ApiUtils;
import com.supercoding.shoppingmallbackend.common.util.JpaUtils;
import com.supercoding.shoppingmallbackend.dto.request.PaymentRequest;
import com.supercoding.shoppingmallbackend.dto.response.PaymentResponse;
import com.supercoding.shoppingmallbackend.dto.response.PurchaseResponse;
import com.supercoding.shoppingmallbackend.dto.response.SaleResponse;
import com.supercoding.shoppingmallbackend.entity.*;
import com.supercoding.shoppingmallbackend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ShoppingCartRepository shoppingCartRepository;
    private final ProfileRepository profileRepository;
    private final ConsumerRepository consumerRepository;
    private final GenreRepository genreRepository;


    @Transactional
    public CommonResponse<List<PaymentResponse>> processPayment(PaymentRequest paymentRequest) {
        // 토큰에서 consumerId 혹은 email 파싱
        Long consumerId = 1L;

        // 재고가 충분한지 확인
        List<ShoppingCart> shoppingCart =  shoppingCartRepository.findAllByConsumerId(consumerId);
        if (shoppingCart.isEmpty()) throw new CustomException(ShoppingCartErrorCode.EMPTY);
        if (shoppingCart.stream().anyMatch(cart->cart.getAmount() > cart.getProduct().getAmount())) throw new CustomException(PaymentErrorCode.OVER_AMOUNT);

        // 페이머니가 충분한지 확인
        Consumer consumer = consumerRepository.findConsumerById(consumerId).orElseThrow(()->new CustomException(ConsumerErrorCode.NOT_FOUND_BY_ID));
        Profile profile = consumer.getProfile();
        Long totalPrice = shoppingCart.stream().mapToLong(el->el.getAmount() * el.getProduct().getPrice()).sum();
        if (profile.getPaymoney() < totalPrice) throw new CustomException(PaymentErrorCode.NOT_ENOUGH_PAYMONEY);

        //주문번호 생성
        String orderNumber = createOrderNumber();
        if (orderNumber == null) throw new CustomException(PaymentErrorCode.FAIL_TO_CREATE);

        //결제일자 생성
        Timestamp paidAt = new Timestamp(new Date().getTime());

        // 상품 재고 차감, 장바구니 소프트 딜리트, 결제내역 추가
        shoppingCart.forEach(cart-> {
            Product product = cart.getProduct();
            product.setAmount(product.getAmount()-cart.getAmount());

            cart.setIsDeleted(true);

            Payment newData = Payment.from(orderNumber, cart, paymentRequest, paidAt);
            JpaUtils.managedSave(paymentRepository, newData);
        });

        // 페이머니 차감
        profile.setPaymoney(profile.getPaymoney() - totalPrice);

        // 결제내역 응답
        List<Payment> payments = paymentRepository.findAllByOrderNumber(orderNumber);
        if (payments.isEmpty()) throw new CustomException(PaymentErrorCode.NO_CREATED_PAYMENT);
        List<PaymentResponse> paymentResponses = payments.stream()
                .map(PaymentResponse::from)
                .collect(Collectors.toList());

        return ApiUtils.success("결제를 성공적으로 완료했습니다.", paymentResponses) ;
    }

    private String createOrderNumber(){
        String dateString = new SimpleDateFormat("yyMMddHH").format(new Date());

        for (int i = 1; i <= 10; i++) {
            int randomInt = new Random().nextInt(0xfff + 1);
            String orderNumber = dateString + Integer.toHexString(randomInt);

            if (!paymentRepository.existsByOrderNumber(orderNumber))
                return orderNumber;

            log.info("중복되는 주문번호 생성: '{}', {}회 시도", orderNumber, i);
        }

        return null;
    }

    public CommonResponse<List<PurchaseResponse>> getPurchaseHistory() {
        // 토큰에서 consumerId 혹은 email 파싱하기
        Long consumerId = 1L;

        // 구매내역 조회하기
        List<Payment> payments = paymentRepository.findAllByConsumerId(consumerId);
        List<PurchaseResponse> purchaseResponses = payments.stream()
                .map(PurchaseResponse::from)
                .collect(Collectors.toList());

        return ApiUtils.success("구매내역을 성공적으로 조회했습니다.", purchaseResponses);
    }

    public CommonResponse<List<SaleResponse>> getSaleHistory() {
        // 토큰에서 consumerId 혹은 email 파싱하기
        Long sellerId = 1L;

        // 판매내역 조회하기
        List<Payment> payments = paymentRepository.findAllBySellerId(sellerId);
        List<SaleResponse> saleResponses = payments.stream()
                .map(SaleResponse::from)
                .collect(Collectors.toList());

        return ApiUtils.success("판매내역을 성공적으로 조회했습니다.", saleResponses);
    }
}
