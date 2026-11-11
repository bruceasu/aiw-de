package me.asu.aidev.execution.pricing;

import jakarta.annotation.PreDestroy;
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

/** Runs a bounded public-source observation after startup and every 24 hours. */
@Component
public class ModelPriceCollectionScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(ModelPriceCollectionScheduler.class);
    private final ModelPriceCollectorProcess collector;
    private final ModelPriceCollectionState state;
    private final ModelPriceRepairMaterials repairs;
    private final ModelPriceRepairDispatch dispatch;
    private final ModelPriceObservationPublisher publisher;
    private final boolean enabled;
    private final AtomicBoolean started = new AtomicBoolean();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "model-price-collection"); thread.setDaemon(true); return thread;
    });

    public ModelPriceCollectionScheduler(ModelPriceCollectorProcess collector, ModelPriceCollectionState state, ModelPriceRepairMaterials repairs, ModelPriceRepairDispatch dispatch,
            ModelPriceObservationPublisher publisher,
            @Value("${aidev.model-pricing.enabled:true}") boolean enabled) {
        this.collector = collector; this.state = state; this.repairs = repairs; this.dispatch = dispatch; this.publisher = publisher; this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (enabled && started.compareAndSet(false, true))
            executor.scheduleWithFixedDelay(this::run, 5, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
    }

    private void run() {
        for (String provider : java.util.List.of("openai", "deepseek")) collect(provider);
    }

    private void collect(String provider) {
        String token = null;
        try {
            token = state.claim(provider, System.currentTimeMillis());
            if (token == null) return;
            var collected = collector.collectDetails(provider);
            state.observed(token, collected);
            try {
                if (!publisher.publish(collected.observation())) LOG.info("No fully understood billable price rules for {}", provider);
            } catch (RuntimeException publicationFailure) {
                LOG.warn("Observed prices were not published for {} ({})", provider, publicationFailure.getClass().getSimpleName());
            }
        } catch (ModelPriceCollectorProcess.StructureChange failure) {
            if (token != null) {
                try { state.structureChanged(token, failure.change()); }
                catch (RuntimeException unavailable) {
                    LOG.warn("Price source change could not be recorded ({})", unavailable.getClass().getSimpleName());
                    return;
                }
                try { String material = repairs.prepare(provider); if (material != null) dispatch.enqueue(material); }
                catch (RuntimeException unavailable) { LOG.warn("Price repair inputs could not be prepared ({})", unavailable.getClass().getSimpleName()); }
            }
            LOG.warn("Public price source structure changed");
        } catch (RuntimeException failure) {
            String code = switch (String.valueOf(failure.getMessage())) {
                case "PRICE_SOURCE_UNAVAILABLE" -> "SOURCE_UNAVAILABLE";
                case "PRICE_COLLECTOR_UNAVAILABLE", "PRICE_COLLECTOR_TIMEOUT", "PRICE_COLLECTOR_INTERRUPTED" -> "COLLECTOR_UNAVAILABLE";
                default -> "COLLECTOR_REJECTED";
            };
            if (token != null) {
                try { state.failed(provider, token, code); }
                catch (RuntimeException unavailable) { LOG.warn("Price collection state could not be recorded ({})", unavailable.getClass().getSimpleName()); }
            }
            LOG.warn("Public price observation failed: {}", code);
        }
    }

    @PreDestroy public void close() { executor.shutdownNow(); }
}
