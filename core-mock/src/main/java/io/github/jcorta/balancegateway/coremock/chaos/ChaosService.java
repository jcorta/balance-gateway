package io.github.jcorta.balancegateway.coremock.chaos;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChaosService {

    private final AtomicReference<ChaosSettings> settings = new AtomicReference<>(ChaosSettings.NONE);
    private final RandomGenerator random;

    public ChaosService(RandomGenerator random) {
        this.random = random;
    }

    public ChaosSettings current() {
        return settings.get();
    }

    public ChaosSettings update(ChaosSettings newSettings) {
        settings.set(newSettings);
        return newSettings;
    }

    public void reset() {
        settings.set(ChaosSettings.NONE);
    }

    /** Outage first, then added latency, then a random failure. */
    public void applyToRequest() {
        ChaosSettings active = settings.get();
        if (active.down()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Core is down");
        }
        sleep(active.latencyMs());
        if (random.nextInt(100) < active.errorRatePercent()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Simulated core error");
        }
    }

    private static void sleep(int millis) {
        if (millis == 0) {
            return;
        }
        try {
            Thread.sleep(Duration.ofMillis(millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Interrupted");
        }
    }
}
