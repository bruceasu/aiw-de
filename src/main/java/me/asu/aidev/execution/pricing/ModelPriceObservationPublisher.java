package me.asu.aidev.execution.pricing;

import org.springframework.stereotype.Service;

/** Publishes the internal price schema. Provider page interpretation belongs to Python. */
@Service
public class ModelPriceObservationPublisher {
    private final ModelPriceCatalog catalog;
    public ModelPriceObservationPublisher(ModelPriceCatalog catalog){this.catalog=catalog;}
    private ModelPriceCatalog.Snapshot snapshot(ModelPriceCollectorProcess.Observation observation){
        return new ModelPriceCatalog.Snapshot("price-"+observation.provider()+"-"+observation.contentSha256()+"-"+observation.observedMs(),
                observation.provider(),observation.sourceUrl(),observation.contentSha256(),observation.collectorVersion(),
                observation.observedMs(),observation.priceRules());
    }
    public boolean canPublish(ModelPriceCollectorProcess.Observation observation){
        if(observation==null||!"micro-currency-per-million-tokens".equals(observation.unit()))return false;
        try{catalog.validate(snapshot(observation));return true;}
        catch(RuntimeException invalid){return false;}
    }
    public boolean publish(ModelPriceCollectorProcess.Observation observation){
        if(!canPublish(observation))return false;
        catalog.publish(snapshot(observation));return true;
    }
}
