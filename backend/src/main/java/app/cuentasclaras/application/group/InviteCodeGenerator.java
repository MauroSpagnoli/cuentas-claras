package app.cuentasclaras.application.group;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Genera codigos de invitacion cortos y legibles (sin caracteres ambiguos
 * como 0/O o 1/I).
 */
@Component
public class InviteCodeGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        char[] code = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            code[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }
        return new String(code);
    }
}
