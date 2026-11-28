package com.hospital.crm.customer.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByCustomerNo(String customerNo);

    Optional<Customer> findFirstByPhoneHashAndStatus(String phoneHash, String status);

    Optional<Customer> findFirstByIdNoHashAndStatus(String idNoHash, String status);

    Optional<Customer> findFirstByNameAndBirthDateAndStatus(String name, LocalDate birthDate, String status);

    List<Customer> findAllByStatusOrderByCreatedAtDesc(String status);

    @Query(value = "select nextval('crm_customer_no_seq')", nativeQuery = true)
    Long nextCustomerNo();
}
