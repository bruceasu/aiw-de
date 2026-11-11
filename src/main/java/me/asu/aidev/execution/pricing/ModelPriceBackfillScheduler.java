package me.asu.aidev.execution.pricing;

import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Revisits missing estimates after price publication without blocking startup. */
@Component
public class ModelPriceBackfillScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(ModelPriceBackfillScheduler.class);
    private final ModelPriceCatalog prices;
    private final boolean enabled;
    private final AtomicBoolean started = new AtomicBoolean();
    // Accessed only by the single scheduler thread. Restart safely starts from the beginning.
    private final Map<String, ModelPriceCatalog.Cursor> cursors = new HashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "model-price-backfill"); thread.setDaemon(true); return thread;
    });

    public ModelPriceBackfillScheduler(ModelPriceCatalog prices,
            @Value("${aidev.model-pricing.enabled:true}") boolean enabled) {
        this.prices = prices; this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (enabled && started.compareAndSet(false, true))
            executor.scheduleWithFixedDelay(this::run, 10, 60, TimeUnit.SECONDS);
    }

    private void run() {
        for (String provider : java.util.List.of("openai", "deepseek")) {
            if (Thread.currentThread().isInterrupted()) return;
            try {
                // Bound each pass; absent prices or incomplete usage stay unknown.
                for (int page = 0; page < 4 && !Thread.currentThread().isInterrupted(); page++) {
                    var result = prices.backfill(provider, 500, cursors.get(provider));
                    if (result.next() == null) {
                        cursors.remove(provider);
                        break;
                    }
                    cursors.put(provider, result.next());
                }
            } catch (RuntimeException failure) {
                // Retry on a later tick; the transactional catalog keeps prior estimates intact.
                LOG.warn("Model price backfill deferred for {} ({})", provider, failure.getClass().getSimpleName());
            }
        }
    }

    @PreDestroy public void close() { executor.shutdownNow(); }
}
