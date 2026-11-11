package me.asu.aidev.execution.pricing;

import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import me.asu.aidev.authority.AuthorityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Bounded background passes do not alter already recorded estimates. */
@Component
public class ModelPricingBackfill {
    private static final Logger LOG = LoggerFactory.getLogger(ModelPricingBackfill.class);
    private record Position(String snapshot, ModelPriceCatalog.Cursor cursor) {}
    private final AuthorityRepository repo;
    private final ModelPriceCatalog prices;
    private final Map<String, Position> positions = new HashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean started = new java.util.concurrent.atomic.AtomicBoolean();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "model-pricing-backfill");
        thread.setDaemon(true);
        return thread;
    });

    public ModelPricingBackfill(AuthorityRepository repo, ModelPriceCatalog prices) {
        this.repo = repo;
        this.prices = prices;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (started.compareAndSet(false, true)) executor.scheduleWithFixedDelay(this::run, 5, 30, TimeUnit.SECONDS);
    }

    private void run() {
        try {
            var heads = repo.jdbc().queryForList("SELECT provider,snapshot_id FROM model_price_head ORDER BY provider LIMIT 32");
            for (var head : heads) {
                String provider = (String)head.get("provider"), snapshot = (String)head.get("snapshot_id");
                try {
                    Position previous = positions.get(provider);
                    var cursor = previous != null && previous.snapshot().equals(snapshot) ? previous.cursor() : null;
                    var result = prices.backfill(provider, 200, cursor);
                    positions.put(provider, new Position(snapshot, result.next()));
                } catch (RuntimeException failure) {
                    LOG.warn("Model pricing backfill for {} could not complete; it will retry later ({})",
                            provider, failure.getClass().getSimpleName());
                }
            }
        } catch (RuntimeException failure) {
            LOG.warn("Model pricing backfill could not complete; it will retry later ({})", failure.getClass().getSimpleName());
        }
    }

    @PreDestroy
    public void close() { executor.shutdownNow(); }
}
