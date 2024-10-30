package com.gizmo.gizmoshop.dto.reponseDto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class ProductResponse {
    private String productName;
    private List<ProductImageMappingResponse> productImageUrl;
    private ProductInventoryResponse quantity;
    private Long productPrice;
    private String productLongDescription;
    private String productShortDescription;
    private Float productWeight;
    private Float productArea;//diện tích
    private Float productVolume;//thể tích
    private BrandResponseDto productBrand;
    private CategoriesResponse productCategories;
    private ProductStatusResponse productStatusResponse;
    private AccountResponse author;


    private LocalDateTime productCreationDate;
    private LocalDateTime productUpdateDate;

    public ProductResponse(String productName, Long productPrice, String productShortDescription) {
        this.productName = productName;
        this.productPrice = productPrice;
        this.productShortDescription = productShortDescription;
    }
}
