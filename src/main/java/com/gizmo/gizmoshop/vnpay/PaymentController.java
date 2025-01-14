package com.gizmo.gizmoshop.vnpay;

import com.gizmo.gizmoshop.dto.reponseDto.ResponseWrapper;
import com.gizmo.gizmoshop.dto.requestDto.OrderRequest;
import com.gizmo.gizmoshop.exception.InvalidInputException;
import com.gizmo.gizmoshop.sercurity.UserPrincipal;
import com.gizmo.gizmoshop.service.OrderService;
import com.gizmo.gizmoshop.service.SupplierService;
import com.gizmo.gizmoshop.utils.EncryptionUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/payment")
@RequiredArgsConstructor
@CrossOrigin("*")
public class PaymentController {
    @Autowired
    private SupplierService supplierService;
    @Autowired
    private  PaymentService paymentService;
    @Autowired
    private OrderService orderService;
    @Value("${customer.url}")
    private String customerUrl;
    @GetMapping("/vn-pay")
    public ResponseWrapper<PaymentDTO.VNPayResponse> pay(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest request) {
        return new ResponseWrapper<>(HttpStatus.OK, "Success", paymentService.createVnPayPayment(request ,userPrincipal.getUserId()));
    }
    @GetMapping("/vn-pay-callback")
    public void payCallbackHandler(HttpServletRequest request, HttpServletResponse response) {
        String status = request.getParameter("vnp_ResponseCode");
        String txnRef = null;
        try {
            txnRef = EncryptionUtil.decrypt(request.getParameter("vnp_TxnRef").toString(),"gizmo");
        } catch (Exception e) {
            new InvalidInputException("Đầu vào TXN Ref không hợp lệ : " + txnRef);
        }
        String amountStr = request.getParameter("vnp_Amount");
        System.out.println("thông tin : "+txnRef);
        String redirectUrl;
        try {
            // Tách txnRef thành cặp key-value
            Map<String, String> txnRefMap = parseTxnRef(txnRef);
            String typeStr = txnRefMap.get("type");
            if (typeStr == null) {
                throw new IllegalArgumentException("Missing transaction type in txnRef");
            }
            TransactionType transactionType = null;
            for (TransactionType type : TransactionType.values()) {
                if (type.getType().equals(typeStr)) {
                    transactionType = type;
                    break;
                }
            }
            if (transactionType == null) {
                throw new IllegalArgumentException("Unknown transaction type: " + typeStr);
            }
            // Xử lý logic tùy theo loại giao dịch
            if ("00".equals(status)) {
                switch (transactionType) {
                    case ORDER_PAYMENT:
                        String voucherId = txnRefMap.get("idVoucher");
                        String accountId = txnRefMap.get("idAccount");
                        String walletId = txnRefMap.get("idWallet");
                        String addressId = txnRefMap.get("idAddress");
                        if (voucherId == null || accountId == null || walletId == null || addressId == null) {
                            throw new IllegalArgumentException("Missing ORDER_PAYMENT parameters in txnRef");
                        }
                        System.out.println("thong tin" + accountId +"- " + walletId +"- "+voucherId);
                        OrderRequest orderRequest = new OrderRequest();
                        orderRequest.setPaymentMethod(false);//online
                        orderRequest.setNote("đặt hàng thành công(online) -- tiếp nhận sử lý");
                        orderRequest.setAddressId(Long.parseLong(addressId));
                        orderRequest.setVoucherId(Long.parseLong(voucherId));
                        orderRequest.setWalletId(Long.parseLong(walletId));
                        orderService.placeOrderBusiness(orderRequest,Long.parseLong(accountId));
                        break;

                    case ACCOUNT_TOPUP:
                        accountId = txnRefMap.get("idAccount");
                        if (accountId == null) {
                            throw new IllegalArgumentException("Missing ACCOUNT_TOPUP parameters in txnRef");
                        }
                        // Gọi service xử lý nạp tiền
                        String sanitizedAmountStr = amountStr.length() > 2
                                ? amountStr.substring(0, amountStr.length() - 2)
                                : "0";
                        supplierService.DepositNoApi(Long.parseLong(accountId),Long.parseLong(sanitizedAmountStr));
                        break;

                    case SUPPLIER_REGISTRATION:
                        accountId = txnRefMap.get("idAccount");
                        walletId = txnRefMap.get("idWallet");
                        if (accountId == null || walletId == null) {
                            throw new InvalidInputException("Missing SUPPLIER_REGISTRATION parameters in txnRef");
                        }
                        // Gọi service xử lý đăng ký nhà cung cấp
                       supplierService.SupplierRegisterBusinessNotApi(Long.parseLong(accountId), Long.parseLong(walletId));
                        break;

                    default:
                        throw new IllegalStateException("Unexpected transaction type: " + transactionType);
                }

                redirectUrl = customerUrl + "/payment/payment-success";
            } else {
                redirectUrl = customerUrl + "/payment/payment-failed";
            }
            redirectUrl += "?status=" + status + "&txnRef=" + VNPayUtil.hmacSHA512(amountStr,txnRef) + "&amount=" + amountStr + (transactionType.equals(TransactionType.SUPPLIER_REGISTRATION) ? "&type=pendingsupplier" : "") ;
            response.sendRedirect(redirectUrl);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Error redirecting: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            System.out.println("Invalid transaction reference: " + e.getMessage());
        }
    }

    // Hàm tách txnRef thành Map
    private Map<String, String> parseTxnRef(String txnRef) {
        Map<String, String> map = new HashMap<>();
        String[] pairs = txnRef.split("\\|");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2) {
                map.put(keyValue[0], keyValue[1]);
            }
        }
        return map;
    }


}
