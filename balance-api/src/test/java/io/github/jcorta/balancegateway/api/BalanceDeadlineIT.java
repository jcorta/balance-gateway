package io.github.jcorta.balancegateway.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

/**
 * The per-attempt read timeout is set above the total deadline, so only the deadline can end a hanging request.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "balance.core.read-timeout=5s",
        "resilience4j.timelimiter.instances.core.timeout-duration=500ms"
})
class BalanceDeadlineIT extends CoreBackedApiSupport {

    @Test
    void answersWithinTheDeadlineWhenTheCoreHangs() {
        core.stubFor(balanceRequest("ACC-HANG").willReturn(
                balanceBody("ACC-HANG", "1.00", "2026-11-26T14:03:12Z").withFixedDelay(3_000)));

        long start = System.nanoTime();
        ResponseEntity<String> response = requestBalance("ACC-HANG");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(elapsed).isBetween(Duration.ofMillis(500), Duration.ofMillis(1_500));
    }

    @Test
    void servesTheLastKnownBalanceWithinTheDeadlineWhenTheCoreHangs() {
        core.stubFor(balanceRequest("ACC-HANG-STALE").willReturn(balanceBody("ACC-HANG-STALE", "2.00", "2026-11-26T14:03:12Z")));
        requestBalance("ACC-HANG-STALE");
        resetCoreStubs();
        core.stubFor(balanceRequest("ACC-HANG-STALE").willReturn(
                balanceBody("ACC-HANG-STALE", "9.99", "2026-11-26T15:00:00Z").withFixedDelay(3_000)));

        long start = System.nanoTime();
        ResponseEntity<String> response = requestBalance("ACC-HANG-STALE");
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains("\"stale\":true").contains("\"balance\":2.00");
        assertThat(elapsed).isLessThan(Duration.ofMillis(1_500));
    }
}
