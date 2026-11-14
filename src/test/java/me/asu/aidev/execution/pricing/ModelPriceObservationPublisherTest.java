package me.asu.aidev.execution.pricing;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModelPriceObservationPublisherTest {
    @Test void publishesInternalRulesWithoutKnowingProviderTableHeadings(){
        var catalog=spy(new ModelPriceCatalog(null,null));doNothing().when(catalog).publish(any());
        var publisher=new ModelPriceObservationPublisher(catalog);
        var rate=new ModelPriceCatalog.Rule("rate","future-model","USD","default",100L,null,"UTC",Set.of(),null,null,Set.of(),Set.of(),null,null,10,2,20,true,null,null,null);
        var observation=new ModelPriceCollectorProcess.Observation("aiw.price-observation.v1","other-provider","https://example.com/prices","a".repeat(64),"parser-v2",100L,"USD","micro-currency-per-million-tokens","OBSERVED_ONLY",false,List.of(),Map.of(),List.of(),"b".repeat(64),List.of(rate));
        assertTrue(publisher.publish(observation));
        verify(catalog).publish(argThat(snapshot->snapshot.rules().equals(List.of(rate))));
    }
    @Test void missingInternalPricesAreNotPublishedAsFreeUsage(){
        var publisher=new ModelPriceObservationPublisher(new ModelPriceCatalog(null,null));
        var observation=new ModelPriceCollectorProcess.Observation("aiw.price-observation.v1","openai","https://example.com/prices","a".repeat(64),"v2",100L,"USD","micro-currency-per-million-tokens","OBSERVED_ONLY",false,List.of(),Map.of(),List.of());
        assertFalse(publisher.canPublish(observation));
    }
}
