package com.hospital.crm.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID>,
        JpaSpecificationExecutor<Invoice> {

    List<Invoice> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    @Query(value = "select nextval('crm_invoice_no_seq')", nativeQuery = true)
    Long nextInvoiceNo();

    /**
     * 订单有效开票累计金额（APPLIED/ISSUED，不含已红冲 VOID）。
     */
    @Query("select coalesce(sum(i.amount), 0) from Invoice i "
            + "where i.orderId = :orderId and i.status in ('APPLIED','ISSUED')")
    BigDecimal sumActiveAmount(@Param("orderId") UUID orderId);
}
