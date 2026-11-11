package me.asu.aidev.execution.pricing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import me.asu.aidev.authority.AuthorityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class ModelPriceCollectorProcessTest {
    @TempDir Path temp;

    @Test void acceptsBoundedObservationWithoutForwardingApiKeyEnvironment() throws Exception {
        Files.writeString(temp.resolve("runner.py"), """
            import base64, hashlib, json, os, sys
            if any(name in os.environ for name in ('OPENAI_API_KEY', 'DEEPSEEK_API_KEY', 'AIW_MODEL_CREDENTIAL_KEY')):
                raise SystemExit(1)
            request = json.load(sys.stdin)
            digest = hashlib.sha256(base64.b64decode(request['contentBase64'])).hexdigest()
            observation = {'protocol':'aiw.price-observation.v1', 'provider':'deepseek',
                'sourceUrl':'https://api-docs.deepseek.com/quick_start/pricing/', 'contentSha256':digest, 'collectorSha256':'0'*64,
                'collectorVersion':'test', 'observedMs':request['observedMs'], 'currency':'USD',
                'unit':'micro-currency-per-million-tokens', 'validity':'OBSERVED_ONLY', 'accountingReady':False,
                'missing':['TRUSTED_EFFECTIVE_INTERVAL','CHINESE_PUBLIC_HOLIDAY_CALENDAR'],
                'conditions':{'kind':'test'}, 'models':[{'name':'sample'}]}
            print(json.dumps({'protocol':'aiw.price-parser-response.v1','state':'OBSERVED','observation':observation}))
            """);
        var service = new ModelPriceCollectorProcess(new AuthorityRepository(new JdbcTemplate()), "python", temp.toString());
        var result = service.parseDeepSeek("example".getBytes(StandardCharsets.UTF_8), 1234);
        assertEquals("deepseek", result.provider());
        assertEquals(1234, result.observedMs());
        assertFalse(result.accountingReady());
    }

    @Test void rejectsOversizedInputBeforeStartingProcess() {
        var service = new ModelPriceCollectorProcess(new AuthorityRepository(new JdbcTemplate()), "python", temp.toString());
        assertThrows(IllegalArgumentException.class, () -> service.parseDeepSeek(new byte[2_000_001], 1234));
    }

    @Test void acceptsAChangedPathOnTheApprovedOfficialHost() throws Exception {
        Files.writeString(temp.resolve("runner.py"), """
            import base64, hashlib, json, sys, time
            request = json.load(sys.stdin)
            content = b'public page'
            observation = {'protocol':'aiw.price-observation.v1', 'provider':'deepseek',
                'sourceUrl':'https://api-docs.deepseek.com/new-pricing',
                'contentSha256':hashlib.sha256(content).hexdigest(), 'collectorVersion':'test','collectorSha256':'0'*64,
                'observedMs':time.time_ns()//1000000, 'currency':'USD',
                'unit':'micro-currency-per-million-tokens', 'validity':'OBSERVED_ONLY', 'accountingReady':False,
                'missing':['TRUSTED_EFFECTIVE_INTERVAL','CHINESE_PUBLIC_HOLIDAY_CALENDAR'],
                'conditions':{'kind':'test'}, 'models':[{'name':'sample'}]}
            print(json.dumps({'protocol':'aiw.price-parser-response.v1','state':'OBSERVED',
                'observation':observation,'contentBase64':base64.b64encode(content).decode('ascii')}))
            """);
        var service = new ModelPriceCollectorProcess(new AuthorityRepository(new JdbcTemplate()), "python", temp.toString());
        assertEquals("https://api-docs.deepseek.com/new-pricing", service.collectDeepSeek().sourceUrl());
    }

    @Test void returnsValidatedSourceSampleForAChangedStructure() throws Exception {
        Files.writeString(temp.resolve("runner.py"), """
            import base64, json, sys, time
            request = json.load(sys.stdin)
            print(json.dumps({'protocol':'aiw.price-parser-response.v1','state':'STRUCTURE_CHANGED',
                'sourceUrl':'https://api-docs.deepseek.com/new-pricing','observedMs':time.time_ns()//1000000,
                'collectorVersion':'v1','collectorSha256':'0'*64,'reason':'PRICE_ROWS_CHANGED',
                'contentBase64':base64.b64encode(b'public page').decode('ascii')}))
            raise SystemExit(1)
            """);
        var service = new ModelPriceCollectorProcess(new AuthorityRepository(new JdbcTemplate()), "python", temp.toString());
        var failure = assertThrows(ModelPriceCollectorProcess.StructureChange.class, service::collectDeepSeek);
        assertEquals("PRICE_ROWS_CHANGED", failure.change().reason());
        assertEquals("public page", new String(java.util.Base64.getDecoder().decode(failure.change().contentBase64()), StandardCharsets.UTF_8));
    }

    @Test void acceptsOpenAiObservationWithoutTreatingItAsAPrice() throws Exception {
        Files.writeString(temp.resolve("runner.py"), """
            import base64, hashlib, json, sys, time
            request = json.load(sys.stdin)
            if request['provider'] != 'openai': raise SystemExit(1)
            content = b'public page'
            observation = {'protocol':'aiw.price-observation.v1','provider':'openai',
                'sourceUrl':'https://developers.openai.com/api/docs/pricing.md',
                'contentSha256':hashlib.sha256(content).hexdigest(),'collectorVersion':'test','collectorSha256':'0'*64,
                'observedMs':time.time_ns()//1000000,'currency':'USD',
                'unit':'micro-currency-per-million-tokens','validity':'OBSERVED_ONLY','accountingReady':False,
                'missing':['TRUSTED_EFFECTIVE_INTERVAL','SERVICE_TIER_AND_PRICING_CONDITIONS'],
                'conditions':{'kind':'test'},'models':[{'name':'sample'}]}
            print(json.dumps({'protocol':'aiw.price-parser-response.v1','state':'OBSERVED',
                'observation':observation,'contentBase64':base64.b64encode(content).decode('ascii')}))
            """);
        var service = new ModelPriceCollectorProcess(new AuthorityRepository(new JdbcTemplate()), "python", temp.toString());
        assertFalse(service.collectOpenAI().accountingReady());
    }
}
