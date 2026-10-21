package com.fleet.storefront.adapter.out.persistence;

import com.fleet.storefront.application.port.out.StaffAttribution;
import com.fleet.storefront.domain.staff.StaffId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
class JdbcStaffAttribution implements StaffAttribution {

    private final JdbcClient jdbc;

    JdbcStaffAttribution(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void attribute(Document document, UUID id, StaffId staff) {
        String table = switch (document) {
            case ORDER -> "customer_order";
            case PAYMENT -> "payment";
            case REFUND -> "refund";
        };
        jdbc.sql("UPDATE " + table + " SET staff_id = :staff WHERE id = :id")
                .param("staff", staff.value())
                .param("id", id)
                .update();
    }
}
