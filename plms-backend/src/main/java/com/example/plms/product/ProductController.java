package com.example.plms.product;

import com.example.plms.common.Actor;
import com.example.plms.common.ApiException;
import com.example.plms.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /** Catalogue: only products open for new applications today. */
    @GetMapping("/api/v1/loan-products")
    public PageResponse<ProductDtos.ProductResponse> list(@RequestParam(required = false) String q,
                                                          @RequestParam(required = false) Integer page,
                                                          @RequestParam(required = false) Integer size) {
        return service.listAvailable(q, page, size);
    }

    /** Customers can only open available products; staff can view any product. */
    @GetMapping("/api/v1/loan-products/{id}")
    public ProductDtos.ProductResponse get(@PathVariable String id, Authentication auth) {
        LoanProduct product = service.get(id);
        if (!Actor.from(auth).isStaff() && !product.isAvailableOn(service.today())) {
            throw ApiException.notFound("Loan product");
        }
        return ProductDtos.ProductResponse.from(product, service.today());
    }

    @GetMapping("/api/v1/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<ProductDtos.ProductResponse> all(@RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) {
        return service.listAll(page, size);
    }

    @PostMapping("/api/v1/admin/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductDtos.ProductResponse> create(@Valid @RequestBody ProductDtos.ProductRequest request,
                                                              Authentication auth) {
        LoanProduct created = service.create(Actor.from(auth).userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductDtos.ProductResponse.from(created, service.today()));
    }

    @PutMapping("/api/v1/admin/products/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductDtos.ProductResponse update(@PathVariable String id,
                                              @Valid @RequestBody ProductDtos.ProductRequest request,
                                              Authentication auth) {
        return ProductDtos.ProductResponse.from(service.update(Actor.from(auth).userId(), id, request), service.today());
    }
}
