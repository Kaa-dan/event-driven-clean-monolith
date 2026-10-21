package com.fleet.storefront.adapter.in.scheduling;

import com.fleet.storefront.application.port.in.ManageStaffUseCase;
import com.fleet.storefront.application.port.in.ManageTerminalsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * First start of a fresh node: registers a manager device for the key in
 * {@code STOREFRONT_BOOTSTRAP_MANAGER_KEY} so an operator can sign in to the back office and register
 * the real devices, and a manager staff account from {@code STOREFRONT_BOOTSTRAP_MANAGER_USERNAME} /
 * {@code _PIN} so a person can sign in on it. Each does nothing once a manager of its kind exists; remove
 * the variables after provisioning.
 */
@Component
class BootstrapManager implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapManager.class);

    private final ManageTerminalsUseCase terminals;
    private final ManageStaffUseCase staff;
    private final String key;
    private final String username;
    private final String pin;

    BootstrapManager(ManageTerminalsUseCase terminals, ManageStaffUseCase staff,
                     @Value("${storefront.security.bootstrap-manager-key:}") String key,
                     @Value("${storefront.security.bootstrap-manager-username:}") String username,
                     @Value("${storefront.security.bootstrap-manager-pin:}") String pin) {
        this.terminals = terminals;
        this.staff = staff;
        this.key = key;
        this.username = username;
        this.pin = pin;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (terminals.bootstrapManager(key)) {
            log.warn("Registered device 'manager-bootstrap' from the bootstrap key. Register named manager "
                    + "devices, revoke 'manager-bootstrap' and remove STOREFRONT_BOOTSTRAP_MANAGER_KEY.");
        }
        if (staff.bootstrapManager(username, pin)) {
            log.warn("Created manager staff account '{}' from the bootstrap variables. Change its PIN and remove "
                    + "STOREFRONT_BOOTSTRAP_MANAGER_USERNAME/_PIN.", username);
        }
    }
}
