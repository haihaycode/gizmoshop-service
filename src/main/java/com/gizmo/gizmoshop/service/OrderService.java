package com.gizmo.gizmoshop.service;

import com.gizmo.gizmoshop.dto.reponseDto.*;
import com.gizmo.gizmoshop.dto.requestDto.OrderRequest;
import com.gizmo.gizmoshop.entity.*;
import com.gizmo.gizmoshop.excel.GenericExporter;
import com.gizmo.gizmoshop.exception.InvalidInputException;
import com.gizmo.gizmoshop.exception.NotFoundException;
import com.gizmo.gizmoshop.exception.NotFoundException;
import com.gizmo.gizmoshop.repository.*;
import com.gizmo.gizmoshop.service.Image.ImageService;
import com.gizmo.gizmoshop.service.product.ProductService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.*;
import java.util.stream.Collectors;

@Service

public class OrderService {
    @Autowired
    private ProductInventoryRepository productInventoryRepository;
    @Autowired
    private SuppilerInfoRepository suppilerInfoRepository;
    @Autowired
    private VoucherRepository voucherRepository;
    @Autowired
    private VoucherToOrderRepository voucherToOrderRepository;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CartItemsRepository cartItemsRepository;
    @Autowired
    private OrderStatusRepository orderStatusRepository;
    @Autowired
    private WithdrawalHistoryRepository withdrawalHistoryRepository;
    @Autowired
    private GenericExporter<VoucherResponse> genericExporter;
    @Autowired
    private OrderDetailRepository orderDetailRepository;

    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private AddressAccountRepository addressAccountRepository;
    @Autowired
    private WalletAccountRepository walletAccountRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private CartService cartService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private ProductService productService;
    @Autowired
    private ShipperInforRepository shipperInforRepository;
    @Autowired
    private ShipperOrderRepository shipperOrderRepository;


    public OrderResponse updateOrder(Long idOrder, OrderResponse orderResponse) {

        Order order = orderRepository.findById(idOrder)
                .orElseThrow(() -> new InvalidInputException("Order không tồn tại"));
        if (orderResponse.getNote() != null) {
            order.setNote(orderResponse.getNote());
        }
        if (orderResponse.getOrderStatus() != null) {
            OrderStatus orderStatus = orderStatusRepository.findById(orderResponse.getOrderStatus().getId())
                    .orElseThrow(() -> new NotFoundException("Không tìm thấy trạng thái Order"));
            if (orderStatus.getId() == 17) {
                List<OrderDetail> orderDetailsList = orderDetailRepository.findByIdOrder(order);
                for (OrderDetail orderDetail : orderDetailsList) {
                    ProductInventory productInventory = productInventoryRepository.findByProductId(orderDetail.getIdProduct().getId()).orElseThrow(()
                            -> new InvalidInputException("không tìm thấy sản phẩm đang của đơn hàng trong kho"));
                    productInventory.setQuantity((int) (productInventory.getQuantity() + orderDetail.getQuantity()));
                    productInventoryRepository.save(productInventory);

                    if (!order.getPaymentMethods()) {

                        WalletAccount walletAccount = walletAccountRepository.findById(order.getIdWallet().getId())
                                .orElseThrow(() -> new RuntimeException("WalletAccount không tồn tại!"));
                        WithdrawalHistory history = new WithdrawalHistory();
                        history.setAccount(walletAccount.getAccount());
                        history.setAmount(order.getTotalPrice());
                        history.setWalletAccount(walletAccount);
                        history.setWithdrawalDate(new Date());
                        history.setNote(
                                "CUSTOMER|Hoàn tiền hủy đơn|PENDING"    
                        );
                        withdrawalHistoryRepository.save(history);
                    }


                }
            }

            order.setOrderStatus(orderStatus);
        }

        Order updatedOrder = orderRepository.save(order);
        return convertToOrderResponse(updatedOrder);
    }


    public Page<OrderResponse> findOrdersByUserIdAndStatusAndDateRange(
            Long userId, Long idStatus, Date startDate, Date endDate, Pageable pageable) {
        return orderRepository.findOrdersByUserIdAndStatusAndDateRange(userId, idStatus, startDate, endDate, pageable)
                .map(this::convertToOrderResponse);
    }

    public Page<OrderResponse> findOrdersByALlWithStatusRoleAndDateRange(String orderCode,
                                                                         Long idStatus, Boolean roleStatus,
                                                                         Boolean idProcessing, Date startDate,
                                                                         Date endDate, Pageable pageable) {

        List<Long> statusList = null;
        if (Boolean.TRUE.equals(roleStatus) && Boolean.FALSE.equals(idProcessing)) {
            statusList = new ArrayList<>();
            statusList.add(20L);
            statusList.add(26L);
        } else if (Boolean.FALSE.equals(roleStatus) && Boolean.TRUE.equals(idProcessing)) {
            statusList = new ArrayList<>();
            statusList.add(1L);
        }
        return orderRepository.findOrdersByALlWithStatusRoleAndDateRange(orderCode, statusList, roleStatus, startDate, endDate, pageable)
                .map(this::convertToOrderResponse);

    }


    public OrderSummaryResponse totalCountOrderAndPrice(
            Long userId, Long idStatus, Date startDate, Date endDate) {
        List<Order> orders = orderRepository.totalOrder(userId, idStatus, startDate, endDate);
        long count = 0;
        long sumPrice = 0;
        for (Order order : orders) {
            count++;
            sumPrice += order.getTotalPrice();
        }
        return OrderSummaryResponse.builder()
                .totalQuantityOrder(count)
                .totalAmountOrder(sumPrice)
                .build();
    }


    public OrderResponse getOrderByPhoneAndOrderCode(String phoneNumber, String orderCode) {
        // Tìm đơn hàng theo orderCode và sdt từ AddressAccount
        Optional<Order> orderOpt = orderRepository.findByOrderCodeAndAddressAccount_Sdt(orderCode, phoneNumber);

        if (!orderOpt.isPresent()) {
            throw new InvalidInputException("Không tìm thấy đơn hàng với orderCode và số điện thoại này.");
        }

        Order order = orderOpt.get();
        return convertToOrderResponse(order);
    }

    private OrderResponse convertToOrderResponse(Order order) {
        ContractResponse contractResponse = null;
        if (order.getContract() != null) {
            contractResponse = ContractResponse.builder()
                    .contractId(order.getContract().getId())
                    .notes(order.getContract().getNotes())
                    .contractMaintenanceFee(order.getContract().getContractMaintenanceFee())
                    .start_date(order.getContract().getStartDate())
                    .expirationDate(order.getContract().getExpireDate())
                    .build();
        }
        Optional<SupplierInfo> supplierInfoOptional = suppilerInfoRepository.findByAccount_Id(order.getIdAccount().getId());
        SupplierInfo supplierInfo = new SupplierInfo();
        if (supplierInfoOptional.isPresent()) {
            supplierInfo = supplierInfoOptional.get();
        }


        List<OrderDetail> orderDetailsList = orderDetailRepository.findByIdOrder(order);

        Optional<VoucherToOrder> optionalVoucherOrder = voucherToOrderRepository.findByOrderId(order.getId());


        return OrderResponse.builder()

                .id(order.getId())
                .paymentMethods(order.getPaymentMethods())
                .account(AccountResponse.builder()
                        .id(order.getIdAccount().getId())
                        .fullname(order.getIdAccount().getFullname())
                        .build())
                .addressAccount(AddressAccountResponse.builder()
                        .fullname(order.getAddressAccount().getFullname())
                        .city(order.getAddressAccount().getCity())
                        .commune(order.getAddressAccount().getCommune())
                        .district(order.getAddressAccount().getDistrict())
                        .specificAddress(order.getAddressAccount().getSpecific_address())
                        .sdt(order.getAddressAccount().getSdt())
                        .build())
                .orderStatus(OrderStatusResponse.builder()
                        .id(order.getOrderStatus().getId())
                        .status(order.getOrderStatus().getStatus())
                        .roleStatus(order.getOrderStatus().getRoleStatus())
                        .build())
                .note(order.getNote())
                .totalPrice(order.getTotalPrice())
                .totalWeight(order.getTotalWeight())
                .orderCode(order.getOrderCode())
                .createOderTime(order.getCreateOderTime())
                .supplierDto(SupplierDto.builder()
                        .tax_code(supplierInfo.getTaxCode())
                        .nameSupplier(supplierInfo.getBusiness_name())
                        .Id(supplierInfo.getId())
                        .description(supplierInfo.getDescription())
                        .build())
                .orderDetails(orderDetailsList.stream().map(orderDetail -> OrderDetailsResponse.builder()
                        .id(orderDetail.getId())
                        .price(orderDetail.getPrice())
                        .quantity(orderDetail.getQuantity())
                        .accept(orderDetail.getAccept())
                        .total(orderDetail.getPrice() * orderDetail.getQuantity())
                        .product(ProductResponse.builder()
                                .id(orderDetail.getIdProduct().getId())
                                .discountProduct(orderDetail.getIdProduct().getDiscountProduct())
                                .productName(orderDetail.getIdProduct().getName())
                                .productStatusResponse(ProductStatusResponse.builder()
                                        .id(orderDetail.getIdProduct().getStatus().getId())
                                        .name(orderDetail.getIdProduct().getStatus().getName())
                                        .build())
                                .productImageMappingResponse(productService.getProductImageMappings(orderDetail.getIdProduct().getId()))
                                .productPrice(orderDetail.getIdProduct().getPrice())
                                .thumbnail(orderDetail.getIdProduct().getThumbnail())
                                .productLongDescription(orderDetail.getIdProduct().getLongDescription())
                                .productShortDescription(orderDetail.getIdProduct().getShortDescription())
                                .productWeight(orderDetail.getIdProduct().getWeight())
                                .productArea(orderDetail.getIdProduct().getArea())
                                .productVolume(orderDetail.getIdProduct().getVolume())
                                .productHeight(orderDetail.getIdProduct().getHeight())
                                .productLength(orderDetail.getIdProduct().getLength())
                                .productBrand(BrandResponseDto.builder().
                                        name(orderDetail.getIdProduct().getBrand().getName())
                                        .build())
                                .productCategories(CategoriesResponse.builder()
                                        .name(orderDetail.getIdProduct().getBrand().getName())
                                        .build())
                                .productInventoryResponse(ProductInventoryResponse.builder()
                                        .inventory(InventoryResponse.builder()
                                                .inventoryName(orderDetail.getIdProduct().getProductInventory().getInventory().getInventoryName())
                                                .build())
                                        .quantity(orderDetail.getIdProduct().getProductInventory().getQuantity())
                                        .build())
                                .build())
                        .build()).collect(Collectors.toList()))
                .contractresponse(contractResponse)
                .vouchers(optionalVoucherOrder.stream().map(voucherOrder -> VoucherToOrderResponse.builder()
                        .id(voucherOrder.getId())
                        .voucherId(voucherOrder.getVoucher().getId())
                        .orderId(order.getId())
                        .usedAt(voucherOrder.getUsedAt())
                        .voucher(VoucherResponse.builder()
                                .id(voucherOrder.getVoucher().getId())
                                .code(voucherOrder.getVoucher().getCode())
                                .description(voucherOrder.getVoucher().getDescription())
                                .discountAmount(voucherOrder.getVoucher().getDiscountAmount())
                                .discountPercent(voucherOrder.getVoucher().getDiscountPercent())
                                .maxDiscountAmount(voucherOrder.getVoucher().getMaxDiscountAmount())
                                .minimumOrderValue(voucherOrder.getVoucher().getMinimumOrderValue())
                                .validFrom(voucherOrder.getVoucher().getValidFrom())
                                .validTo(voucherOrder.getVoucher().getValidTo())
                                .usageLimit(voucherOrder.getVoucher().getUsageLimit())
                                .usedCount(voucherOrder.getVoucher().getUsedCount())
                                .status(voucherOrder.getVoucher().getStatus())
                                .build())
                        .build()).collect(Collectors.toList()))
                .build();
    }

    public String cancelOrderForUsers(long idOrder, String note) {
        Optional<Order> order = orderRepository.findById(idOrder);
        if (!order.isPresent()) {
            throw new InvalidInputException("Không tìm thấy đơn hàng ");
        }
        //kiem tra donhang hien tai co phai Đơn hàng đang chờ xét duyệt khong
        if (order.get().getOrderStatus().getId() != 1L) {
            throw new InvalidInputException("Đơn hàng không thể hủy vì đã được xác nhận bởi nhân viên");
        }
        //25 ,là Đơn hàng của người dùng đã hủy thành công
        Optional<OrderStatus> statusCancel = orderStatusRepository.findById(25L);
        if (!statusCancel.isPresent()) {
            throw new InvalidInputException("Không tìm trạng thái Đơn hàng đã hủy và đang đợi xét duyệt");
        }
        order.get().setOrderStatus(statusCancel.get());
        order.get().setNote(note);

        //+ sl ve cho moi sp
        List<OrderDetail> orderDetailList = orderDetailRepository.findByIdOrder(order.get());
        for (OrderDetail orderDetail : orderDetailList) {
            ProductInventory productInventory = productInventoryRepository.findByProductId(orderDetail.getIdProduct().getId()).orElseThrow(() -> new InvalidInputException("không tìm thấy số lượng của sản phẩm"));
            productInventory.setQuantity(productInventory.getQuantity() + Integer.parseInt(String.valueOf(orderDetail.getQuantity())));
            productInventoryRepository.save(productInventory);
        }
        //tạo giao dịch nếu đó là đơn chuyển khoảng

        if (!order.get().getPaymentMethods()) {
            //thanh toan online
            WithdrawalHistory gd = new WithdrawalHistory();

            gd.setWalletAccount(order.get().getIdWallet());
            gd.setAccount(order.get().getIdAccount());
            gd.setAmount(order.get().getTotalPrice());
            gd.setWithdrawalDate(new Date());
            gd.setNote("CUSTOMER|" + note + "|" + "PENDING");

            withdrawalHistoryRepository.save(gd);
        }
        orderRepository.save(order.get());
        return statusCancel.get().getStatus();
    }

    @Transactional
    public void placeOrder(Long accountId, OrderRequest orderRequest) {

        // Kiểm tra xem giỏ hàng có tồn tại hay không
        Cart cart = cartRepository.findByAccountId(accountId)
                .orElseThrow(() -> new InvalidInputException("Giỏ hàng không tồn tại"));

        if (cart.getItems().isEmpty()) {
            throw new InvalidInputException("Giỏ hàng không có sản phẩm");
        }

        long totalAmount = 0;
        float totalWeight = 0.0f;

        for (CartItems cartItem : cart.getItems()) {
            if (cartItem.getQuantity() <= 0) {
                throw new InvalidInputException("Số lượng sản phẩm không hợp lệ trong giỏ hàng");
            }

            Product product = productRepository.findById(cartItem.getProductId().getId())
                    .orElseThrow(() -> new InvalidInputException("Sản phẩm không tồn tại"));

            ProductInventory productInventory = product.getProductInventory();

            if (productInventory == null || productInventory.getQuantity() < cartItem.getQuantity()) {
                throw new InvalidInputException("Sản phẩm không đủ trong kho");
            }

            totalAmount = cart.getTotalPrice();
            totalWeight += cartItem.getQuantity() * product.getWeight();

        }
        long fixedCost = 20000;
        long phiduytri = 10000;

        long weightCost = (long) (totalWeight * 3000);

        totalAmount += fixedCost + weightCost + phiduytri;


        Long addressId = orderRequest.getAddressId();
        Boolean paymentMethod = orderRequest.getPaymentMethod();
        Long walletId = orderRequest.getWalletId();
        Long voucherId = orderRequest.getVoucherId();

        // Kiểm tra địa chỉ giao hàng
        AddressAccount address = addressAccountRepository.findById(addressId)
                .orElseThrow(() -> new InvalidInputException("Địa chỉ giao hàng không tồn tại"));

        // Kiểm tra ví
        WalletAccount wallet = walletAccountRepository.findById(walletId)
                .orElseThrow(() -> new InvalidInputException("Ví không tồn tại"));

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new InvalidInputException("Tài khoản không tồn tại"));

        OrderStatus orderStatus = orderStatusRepository.findById(1L)
                .orElseThrow(() -> new InvalidInputException("Trạng thái đơn hàng không tồn tại"));
        BigDecimal discountAmount = BigDecimal.ZERO;

        if (voucherId != null) {

            Voucher voucher = voucherRepository.findById(voucherId)
                    .orElseThrow(() -> new InvalidInputException("Voucher không tồn tại"));

            if (paymentMethod == null) {
                throw new InvalidInputException("Phương thức thanh toán không được để trống");
            }
            // Kiểm tra trạng thái và hiệu lực của voucher
            if (!voucher.getStatus() || voucher.getValidTo().isBefore(LocalDateTime.now())) {
                throw new InvalidInputException("Voucher không hợp lệ hoặc đã hết hạn");
            }

            // Kiểm tra giới hạn sử dụng
            if (voucher.getUsageLimit() != null && voucher.getUsedCount() >= voucher.getUsageLimit()) {
                throw new InvalidInputException("Voucher đã đạt giới hạn sử dụng");
            }

            // Kiểm tra giá trị tối thiểu của đơn hàng để áp dụng mã giảm giá.
            if (voucher.getMinimumOrderValue() != null && BigDecimal.valueOf(totalAmount).compareTo(voucher.getMinimumOrderValue()) < 0) {
                throw new InvalidInputException("Đơn hàng không đạt giá trị tối thiểu để sử dụng voucher");
            }

            if (voucher.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0
                    && voucher.getDiscountAmount().compareTo(BigDecimal.ZERO) == 0
                    && BigDecimal.valueOf(totalAmount).compareTo(voucher.getMinimumOrderValue()) >= 0) {
                // Tính giảm giá theo phần trăm
                discountAmount = BigDecimal.valueOf(totalAmount)
                        .multiply(voucher.getDiscountPercent().divide(BigDecimal.valueOf(100)));

                // Kiểm tra giới hạn giảm giá tối đa
                if (voucher.getMaxDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                        && discountAmount.compareTo(voucher.getMaxDiscountAmount()) > 0) {
                    discountAmount = voucher.getMaxDiscountAmount();
                }

            } else if (voucher.getDiscountPercent().compareTo(BigDecimal.ZERO) == 0
                    && voucher.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                    && BigDecimal.valueOf(totalAmount).compareTo(voucher.getMinimumOrderValue()) >= 0) {
                // Tính giảm giá cố định
                discountAmount = voucher.getDiscountAmount();
            } else {
                // Không đủ điều kiện áp dụng voucher
                discountAmount = BigDecimal.ZERO;

            }

            // Cập nhật số lần sử dụng voucher
            voucher.setUsedCount(voucher.getUsedCount() + 1);
            voucherRepository.save(voucher);
        }


        // Tạo mã đơn hàng ngẫu nhiên
        String orderCode = generateOrderCode(accountId);
        System.err.println("Mã voucher" + orderRequest.getVoucherId());
        System.err.println("Giá tiền giảm" + totalAmount);
        System.err.println("Giá tiền trước khi giảm" + discountAmount);
        BigDecimal finalTotalPrice = BigDecimal.valueOf(totalAmount).subtract(discountAmount);
        System.err.println("Giá tiền sau khi giảm " + finalTotalPrice);
        BigDecimal productTotalAfterVoucher = BigDecimal.valueOf(totalAmount - (fixedCost + weightCost + phiduytri))
                .subtract(discountAmount);
        System.err.println("Giá tiền sau khi trừ " + productTotalAfterVoucher);
        String noteWithCosts = "Giá ban đầu: " + productTotalAfterVoucher + "VND, Phí vận chuyển: " + weightCost + " VND, Phí cố định: " + fixedCost + "VND, Phí duy trì : " + phiduytri + " VND, Ghi chú: " + orderRequest.getNote();

        Order order = new Order();
        order.setIdAccount(account);
        order.setFixedCost(fixedCost);
        order.setAddressAccount(address);
        order.setPaymentMethods(paymentMethod);
        order.setIdWallet(wallet);
        order.setNote(noteWithCosts);
        order.setTotalWeight(totalWeight);
        order.setOrderCode(orderCode);
        order.setCreateOderTime(new Date());
        order.setTotalPrice(finalTotalPrice.longValue());
        order.setOrderStatus(orderStatus);
        orderRepository.save(order);

        // Lưu thông tin voucher được áp dụng (nếu có)
        if (voucherId != null) {
            VoucherToOrder voucherToOrder = new VoucherToOrder();
            voucherToOrder.setOrder(order);
            voucherToOrder.setVoucher(voucherRepository.findById(voucherId)
                    .orElseThrow(() -> new InvalidInputException("Voucher không tồn tại")));
            voucherToOrder.setUsedAt(LocalDateTime.now());
            voucherToOrderRepository.save(voucherToOrder);
        }

        // Tạo OrderDetails từ các sản phẩm trong giỏ hàng
        for (CartItems cartItem : cart.getItems()) {
            OrderDetail orderDetails = new OrderDetail();
            orderDetails.setIdOrder(order);
            orderDetails.setIdProduct(cartItem.getProductId());
            orderDetails.setPrice(cartItem.getProductId().getPrice());
            orderDetails.setQuantity(cartItem.getQuantity());
            orderDetails.setTotal(cartItem.getQuantity() * cartItem.getProductId().getPrice());
            orderDetails.setAccept(false);  // Đơn hàng chưa được xét duyệt
            orderDetailRepository.save(orderDetails);

            // Giảm số lượng sản phẩm trong kho
            ProductInventory productInventory = cartItem.getProductId().getProductInventory();
            if (productInventory != null) {
                // Chuyển đổi `Long` sang `int` với kiểm tra giá trị
                if (cartItem.getQuantity() > Integer.MAX_VALUE) {
                    throw new InvalidInputException("Số lượng trong giỏ hàng vượt quá giới hạn cho phép");
                }
                int cartItemQuantity = cartItem.getQuantity().intValue(); // Chuyển đổi từ Long sang int

                int updatedQuantity = productInventory.getQuantity() - cartItemQuantity;
                if (updatedQuantity < 0) {
                    throw new InvalidInputException("Không đủ số lượng sản phẩm trong kho");
                }
                productInventory.setQuantity(updatedQuantity);
                productInventoryRepository.save(productInventory);
            }
        }

        // Xóa các sản phẩm trong giỏ hàng
        if (order.getPaymentMethods()) {//COD
            cartItemsRepository.deleteByCartId(cart.getId());
            cart.setTotalPrice(0L); // Reset lại giá trị TotalPrice của giỏ hàng
            cartRepository.save(cart);
        }
    }

    private String generateOrderCode(Long accountId) {
        // Sinh mã đơn hàng ngẫu nhiên theo định dạng: ORD_ddMMyyyy_accountId
        LocalDate currentDate = LocalDate.now();
        String datePart = currentDate.format(DateTimeFormatter.ofPattern("ddMMyyyy")); // ddMMyyyy

        // Sinh 4 số ngẫu nhiên
        Random random = new Random();
        int randomNumber = 1000 + random.nextInt(9000); // Tạo số ngẫu nhiên trong khoảng 1000 đến 9999

        // Tạo mã đơn hàng theo định dạng yêu cầu
        return "ORD_" + datePart + "_" + randomNumber + "_" + accountId;
    }

    public Boolean placeOrderBusiness(OrderRequest orderRequest, long accountId) {
        placeOrder(accountId, orderRequest);
        accountService.resetTxn_ref_vnp(accountId);
        cartService.clearCart(accountId);
        return true;
    }

    @Transactional
    public ShipperInfor reassignDeliveryTasks(long accountId,long idStaffDeliveryStock){
        // Chuyển đơn hàng cho các nhân viên gốc vd:NGUYỄN VĂN BƯU (ĐỂ ĐI GIAO CHO BƯU ĐIỆN RỒI CẬP NHÂ TRẠNG THÁI NHƯ BTH)
        // Lý do không phân đều cho các nhân viên ? vì : mỗi người trực thuộc 2 QUẬN K THỂ PHÂN ĐỀU
        Account account = accountRepository.findById(accountId).orElseThrow(() ->
                new InvalidInputException("Tài khoản không tồn tại"));//tìm nhân viên giao hàng nghỉ tạm thời
        Account accountStaffDeliveryStock = accountRepository.findById(idStaffDeliveryStock).orElseThrow(() ->
                new InvalidInputException("Tài khoản không tồn tại"));// tìm nhân viên gốc //vd : 35 NGUYỄN VĂN BƯU
        ShipperInfor shipper = shipperInforRepository.findByAccountId(account)
                .orElseThrow(() -> new InvalidInputException("Nhân viên giao hàng nghỉ không tồn tại"));
        ShipperInfor StaffDeliveryStock = shipperInforRepository.findByAccountId(accountStaffDeliveryStock)
                .orElseThrow(() -> new InvalidInputException("Nhân viên giao hàng gốc không tồn tại"));
        String placesOfActivity = shipper.getPlaces_of_activity();
        String[] places_of_activity = placesOfActivity.split("\\s*,\\s*");
        String places_of_activity_1 = places_of_activity[0];
        String places_of_activity_2 = (places_of_activity.length > 1 && places_of_activity[1] != null && !places_of_activity[1].isEmpty())
                ? places_of_activity[1]
                : " ";
        List<Order> orders = orderRepository.findAllOrderByPlacesOfActivity1AndPlacesOfActivity2(places_of_activity_1.trim(),places_of_activity_2.trim());
        for (Order order : orders) {
            //nhận từng đơn cho ng thay thế
            ShipperOrder shipperOrder = new ShipperOrder();
            shipperOrder.setOrderId(order);
            shipperOrder.setShipperInforId(StaffDeliveryStock);
            shipperOrderRepository.save(shipperOrder);
            OrderStatus assignedStatus = null ;
            if(!order.getOrderStatus().getRoleStatus()){
                assignedStatus = orderStatusRepository.findById(15L)  //chuyển trạng thái đơn cho người dùng
                        .orElseThrow(() -> new InvalidInputException("Trạng thái đơn hàng của người dùng không tồn tại"));
            }else {
                assignedStatus = orderStatusRepository.findById(29L)  //chuyển trạng thái đơn nhà cung cấp
                        .orElseThrow(() -> new InvalidInputException("Trạng thái đơn hàng của nhà cung cấp không tồn tại"));
            }
            order.setOrderStatus(assignedStatus);
            orderRepository.save(order);
        }
        return StaffDeliveryStock;
    }

}
