package com.baseielts.platform.identity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/** In-process development/test capture. No HTTP, logs, database copy, or production provider. */
@Component
@Profile("(dev | test) & !prod & !production")
@ConditionalOnProperty(name = "base.identity.verification.capture", havingValue = "true")
final class DevelopmentVerificationMailbox {
    private static final int CAPACITY = 32;
    private final Semaphore slots = new Semaphore(CAPACITY);
    private final ArrayDeque<Message> messages = new ArrayDeque<>();
    private final Clock clock = Clock.systemUTC();

    Reservation tryReserve() {
        synchronized (messages) {
            Instant now = clock.instant();
            while (!messages.isEmpty() && !messages.getFirst().expiresAt().isAfter(now)) {
                messages.removeFirst();
                slots.release();
            }
            return slots.tryAcquire() ? new Reservation() : null;
        }
    }

    Optional<Message> takeForTest(String canonicalEmail) {
        synchronized (messages) {
            Instant now = clock.instant();
            var iterator = messages.iterator();
            while (iterator.hasNext()) {
                Message message = iterator.next();
                if (!message.expiresAt().isAfter(now)) {
                    iterator.remove();
                    slots.release();
                } else if (message.canonicalEmail().equals(canonicalEmail)) {
                    iterator.remove();
                    slots.release();
                    return Optional.of(message);
                }
            }
            return Optional.empty();
        }
    }

    record Message(String canonicalEmail, String token, Instant expiresAt) {
        @Override
        public String toString() {
            return "VerificationMessage[redacted]";
        }
    }

    final class Reservation implements AutoCloseable {
        private boolean released;

        void capture(String canonicalEmail, String token) {
            synchronized (messages) {
                if (released) {
                    throw new IllegalStateException("Mailbox reservation was already closed");
                }
                messages.add(new Message(canonicalEmail, token, clock.instant().plus(Duration.ofMinutes(30))));
                released = true;
            }
        }

        @Override
        public void close() {
            synchronized (messages) {
                if (!released) {
                    slots.release();
                    released = true;
                }
            }
        }
    }
}
