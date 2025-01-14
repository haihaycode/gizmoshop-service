package com.gizmo.gizmoshop.controller;

import com.gizmo.gizmoshop.dto.reponseDto.OrderResponse;
import com.gizmo.gizmoshop.dto.reponseDto.OrderSummaryResponse;
import com.gizmo.gizmoshop.dto.reponseDto.ResponseWrapper;
import com.gizmo.gizmoshop.dto.requestDto.OrderRequest;
import com.gizmo.gizmoshop.sercurity.UserPrincipal;
import com.gizmo.gizmoshop.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;

@RestController
@RequestMapping("/api/public/orders")
@RequiredArgsConstructor
@CrossOrigin("*")
public class OrderAPI {
    @Autowired
    OrderService orderService;

    @GetMapping("/OrderForUser")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseWrapper<Page<OrderResponse>>> getOrdersByPhoneOrOrderCode(
            @RequestParam(required = false) Long idStatus, // ID trạng thái đơn hàng (tuỳ chọn)
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate, // Ngày bắt đầu (tuỳ chọn)
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate, // Ngày kết thúc (tuỳ chọn)
            @RequestParam(defaultValue = "0") int page,  // Trang hiện tại (mặc định là 0)
            @RequestParam(defaultValue = "7") int limit, // Số lượng đơn hàng mỗi trang (mặc định là 7)
            @RequestParam(required = false) Optional<String> sort,
            @AuthenticationPrincipal UserPrincipal user) {

        Long accountId = user.getUserId();
        String sortField = "id";
        Sort.Direction sortDirection = Sort.Direction.ASC;

        if (sort.isPresent()) {
            String[] sortParams = sort.get().split(",");
            sortField = sortParams[0];
            if (sortParams.length > 1) {
                sortDirection = Sort.Direction.fromString(sortParams[1]);
            }
        }

        // Tạo đối tượng Pageable với các tham số phân trang và sắp xếp
        Pageable pageable = PageRequest.of(page, limit, Sort.by(sortDirection, sortField));

        // Gọi service để lấy danh sách đơn hàng tìm theo số điện thoại hoặc mã đơn hàng
        Page<OrderResponse> orderResponses = orderService.findOrdersByUserIdAndStatusAndDateRange(accountId, idStatus, startDate, endDate, pageable);

        // Tạo ResponseWrapper và trả về kết quả
        ResponseWrapper<Page<OrderResponse>> responseWrapper = new ResponseWrapper<>(HttpStatus.OK, "Success", orderResponses);
        return ResponseEntity.ok(responseWrapper);
    }

    @GetMapping("/tracuuorder")
    public ResponseEntity<ResponseWrapper<OrderResponse>> getOrderByOrderCodeAndPhoneNumber(
            @RequestParam String orderCode,
            @RequestParam String sdt) {

        OrderResponse orderResponse = orderService.getOrderByPhoneAndOrderCode(sdt, orderCode);

        // Trả về kết quả thành công
        ResponseWrapper<OrderResponse> responseWrapper = new ResponseWrapper<>(HttpStatus.OK, "Tra cứu thành công", orderResponse);
        return ResponseEntity.ok(responseWrapper);
    }

    @GetMapping("/orderSummary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseWrapper<OrderSummaryResponse>> OrderSummaryResponse(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
            @AuthenticationPrincipal UserPrincipal user) {

        Long accountId = user.getUserId();
        OrderSummaryResponse orderResponses = orderService.totalCountOrderAndPrice(accountId,13L, startDate,endDate);
        ResponseWrapper<OrderSummaryResponse> responseWrapper = new ResponseWrapper<>(HttpStatus.OK, "Success", orderResponses);
        return ResponseEntity.ok(responseWrapper);
    }

    @PutMapping("/updateOrder/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_STAFF','ROLE_SUPPLIER')")
    public ResponseEntity<ResponseWrapper<OrderResponse>> updateOrder(@PathVariable("id") Long idOrder,
                                                                      @RequestBody OrderResponse res) {
        OrderResponse orderResponses = orderService.updateOrder(idOrder, res);
        ResponseWrapper<OrderResponse> responseWrapper = new ResponseWrapper<>(HttpStatus.OK, "Success", orderResponses);
        return ResponseEntity.ok(responseWrapper);
    }


    @GetMapping("/orderall")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_STAFF')")
    public ResponseEntity<ResponseWrapper<Page<OrderResponse>>> getOrdersAll(
            @RequestParam(required = false) String orderCode,
            @RequestParam(required = false) Boolean idRoleStatus,
            @RequestParam(required = false) Boolean idProcessing,
            @RequestParam(required = false) Long idStatus, // ID trạng thái đơn hàng (tuỳ chọn)
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate, // Ngày bắt đầu (tuỳ chọn)
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate, // Ngày kết thúc (tuỳ chọn)
            @RequestParam(defaultValue = "0") int page,  // Trang hiện tại (mặc định là 0)
            @RequestParam(defaultValue = "5") int limit, // Số lượng đơn hàng mỗi trang (mặc định là 7)
            @RequestParam(required = false) Optional<String> sort) {

        String sortField = "id";
        Sort.Direction sortDirection = Sort.Direction.ASC;

        if (sort.isPresent()) {
            String[] sortParams = sort.get().split(",");
            sortField = sortParams[0];
            if (sortParams.length > 1) {
                sortDirection = Sort.Direction.fromString(sortParams[1]);
            }
        }

        // Tạo đối tượng Pageable với các tham số phân trang và sắp xếp
        Pageable pageable = PageRequest.of(page, limit, Sort.by(sortDirection, sortField));

        // Gọi service để lấy danh sách đơn hàng tìm theo số điện thoại hoặc mã đơn hàng
        Page<OrderResponse> orderResponses = orderService.findOrdersByALlWithStatusRoleAndDateRange(orderCode,idStatus, idRoleStatus,idProcessing, startDate, endDate, pageable);

        // Tạo ResponseWrapper và trả về kết quả
        ResponseWrapper<Page<OrderResponse>> responseWrapper = new ResponseWrapper<>(HttpStatus.OK, "Success", orderResponses);
        return ResponseEntity.ok(responseWrapper);
    }
    @PostMapping("/OrderPlace")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseWrapper<Void>>placeOrder(
            @RequestBody OrderRequest orderRequest,
            @AuthenticationPrincipal UserPrincipal user) {
        Long accountId = user.getUserId();
        orderService.placeOrder(accountId,orderRequest);

        ResponseWrapper<Void> responseWrapper = new ResponseWrapper<>(HttpStatus.OK, "Đặt hàng thành công và đang chờ xét duyệt", null);

        return ResponseEntity.ok(responseWrapper);
    }
    // api hủy đơn hàng cho người dùng nếu , trạng thái cu đơn hàng đang Đơn hàng đang chờ xét duyệt
    //    // status = 1
    //    //
    @GetMapping("/cancelOrderForUsers/{idOrder}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseWrapper<String>> cancelOrderForUsers(
            @PathVariable Long idOrder,
            @RequestParam(required = false) String note) {
        String status =  orderService.cancelOrderForUsers(idOrder,note);
        ResponseWrapper<String> responseWrapper = new ResponseWrapper<>(HttpStatus.OK, status, null);
        return ResponseEntity.ok(responseWrapper);

    }
}
