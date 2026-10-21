package com.fleet.storefront.application.port.out;

import com.fleet.storefront.domain.staff.StaffId;

import java.util.UUID;

/**
 * Records which person issued a document. Kept out of the fiscal documents themselves (and their hash
 * chain): who pressed the button is an audit fact, not part of the receipt.
 */
public interface StaffAttribution {

    enum Document { ORDER, PAYMENT, REFUND }

    void attribute(Document document, UUID id, StaffId staff);
}
