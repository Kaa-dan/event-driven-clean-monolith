package com.fleet.storefront.application.port.out;

/**
 * Slow, salted hashing for staff PINs. PINs have little entropy, so unlike API keys they need a
 * password KDF (and the lockout in {@code Staff}) rather than a plain digest.
 */
public interface PinHasher {

    String hash(String pin);

    boolean matches(String pin, String hash);
}
