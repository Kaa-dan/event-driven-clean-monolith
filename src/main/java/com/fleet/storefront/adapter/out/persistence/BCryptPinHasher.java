package com.fleet.storefront.adapter.out.persistence;

import com.fleet.storefront.application.port.out.PinHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** BCrypt for staff PINs. A {@code null} hash is checked against a dummy so misses cost the same time. */
@Component
class BCryptPinHasher implements PinHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
    private final String dummy = encoder.encode("not-a-pin");

    @Override
    public String hash(String pin) {
        return encoder.encode(pin);
    }

    @Override
    public boolean matches(String pin, String hash) {
        if (hash == null) {
            encoder.matches(pin, dummy);
            return false;
        }
        return encoder.matches(pin, hash);
    }
}
