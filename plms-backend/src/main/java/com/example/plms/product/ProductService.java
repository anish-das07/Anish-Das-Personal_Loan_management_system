package com.example.plms.product;

import com.example.plms.audit.AuditService;
import com.example.plms.common.ApiException;
import com.example.plms.common.PageResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@Service
public class ProductService {

    private final LoanProductRepository repository;
    private final AuditService audit;
    private final Clock clock;

    public ProductService(LoanProductRepository repository, AuditService audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductDtos.ProductResponse> listAvailable(String q, Integer page, Integer size) {
        var pageable = PageResponse.pageable(page, size, "name,asc", Set.of("name"), "name");
        LocalDate today = today();
        return PageResponse.of(repository.findAvailable(today, q == null ? "" : q.trim(), pageable),
                p -> ProductDtos.ProductResponse.from(p, today));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductDtos.ProductResponse> listAll(Integer page, Integer size) {
        var pageable = PageResponse.pageable(page, size, "name,asc", Set.of("name"), "name");
        LocalDate today = today();
        return PageResponse.of(repository.findAll(pageable), p -> ProductDtos.ProductResponse.from(p, today));
    }

    @Transactional(readOnly = true)
    public LoanProduct get(String id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("Loan product"));
    }

    /** A product that can receive new applications today. */
    @Transactional(readOnly = true)
    public LoanProduct getAvailable(String id) {
        LoanProduct product = get(id);
        if (!product.isAvailableOn(today())) {
            throw ApiException.conflict("PRODUCT_NOT_AVAILABLE", "This loan product is not open for new applications.");
        }
        return product;
    }

    @Transactional
    public LoanProduct create(String adminId, ProductDtos.ProductRequest request) {
        if (repository.findByCode(request.code()).isPresent()) {
            throw ApiException.conflict("PRODUCT_CODE_EXISTS", "A product with this code already exists.");
        }
        LoanProduct product = new LoanProduct(request.code());
        apply(product, request);
        product = repository.save(product);
        audit.record(adminId, "PRODUCT_CREATED", "LOAN_PRODUCT", product.getId(), "SUCCESS",
                Map.of("code", product.getCode()));
        return product;
    }

    /** Edits never alter submitted applications, which keep their own terms snapshot. */
    @Transactional
    public LoanProduct update(String adminId, String id, ProductDtos.ProductRequest request) {
        LoanProduct product = get(id);
        if (!product.getCode().equals(request.code())) {
            throw ApiException.badRequest("PRODUCT_CODE_IMMUTABLE", "The product code cannot be changed.");
        }
        apply(product, request);
        audit.record(adminId, "PRODUCT_UPDATED", "LOAN_PRODUCT", id, "SUCCESS", Map.of("code", product.getCode()));
        return product;
    }

    private static void apply(LoanProduct product, ProductDtos.ProductRequest request) {
        try {
            product.apply(request);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("VALIDATION_FAILED", e.getMessage());
        }
    }
}
