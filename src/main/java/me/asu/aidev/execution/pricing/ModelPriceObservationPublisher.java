package me.asu.aidev.execution.pricing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.asu.aidev.authority.AuthorityRepository;
import org.springframework.stereotype.Service;

/** Converts only explicitly understood source rows to accounting rules. */
@Service
public class ModelPriceObservationPublisher {
    private static final List<String> OPENAI_SHORT = List.of("Model", "Input", "Output", "Cached input");
    private static final List<String> WEEK = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
    private final ModelPriceCatalog catalog;

    public ModelPriceObservationPublisher(ModelPriceCatalog catalog) { this.catalog = catalog; }

    public boolean canPublish(ModelPriceCollectorProcess.Observation observation) {
        return observation != null && observation.observedMs() > 0 && "USD".equals(observation.currency())
                && "micro-currency-per-million-tokens".equals(observation.unit()) && !rules(observation).isEmpty();
    }

    public boolean publish(ModelPriceCollectorProcess.Observation observation) {
        if (!canPublish(observation)) return false;
        List<ModelPriceCatalog.Rule> rules = rules(observation);
        if (rules.isEmpty()) return false;
        String id = "price-" + observation.provider() + "-" + observation.contentSha256() + "-" + observation.observedMs();
        catalog.publish(new ModelPriceCatalog.Snapshot(id, observation.provider(), observation.sourceUrl(),
                observation.contentSha256(), observation.collectorVersion(), observation.observedMs(), rules));
        return true;
    }

    private static List<ModelPriceCatalog.Rule> rules(ModelPriceCollectorProcess.Observation observation) {
        if (observation == null) return List.of();
        return switch (observation.provider()) {
            case "deepseek" -> deepseek(observation);
            case "openai" -> openAi(observation);
            default -> List.of();
        };
    }

    private static List<ModelPriceCatalog.Rule> deepseek(ModelPriceCollectorProcess.Observation observation) {
        Map<String,Object> conditions = observation.conditions();
        if (conditions == null || !"UTC".equals(conditions.get("zone"))) return List.of();
        List<String> peakDays = strings(conditions.get("peakDays"));
        List<List<String>> peakWindows = windows(conditions.get("peakWindows"));
        if (peakDays == null || peakDays.isEmpty() || !WEEK.containsAll(peakDays)
                || peakWindows == null || peakWindows.isEmpty()
                || !"CHINESE_PUBLIC_HOLIDAYS".equals(conditions.get("excluded"))) return List.of();
        var rules = new ArrayList<ModelPriceCatalog.Rule>();
        var ids = new HashSet<String>();
        for (Map<String,Object> model : observation.models()) {
            String name = model == null ? null : (String)model.get("model");
            if (!modelName(name)) return List.of();
            Map<String,Object> off = object(model.get("off_peak"));
            Long input = number(off.get("input")), cached = number(off.get("cached")), output = number(off.get("output"));
            if (input == null || cached == null || output == null) continue;
            for (String day : WEEK) {
                if (!peakDays.contains(day)) {
                    add(rules, ids, observation, name, day, null, null, input, cached, output);
                    continue;
                }
                var sorted = peakWindows.stream().sorted(java.util.Comparator.comparing(w -> w.getFirst())).toList();
                String from = "00:00";
                for (List<String> window : sorted) {
                    String start = window.getFirst(), end = window.get(1);
                    if (!time(from) || !time(start) || !time(end) || start.compareTo(from) < 0 || start.equals(end)) return List.of();
                    if (start.compareTo(from) > 0) add(rules, ids, observation, name, day, from, start, input, cached, output);
                    from = end;
                }
                if (!time(from) || from.compareTo("23:59") >= 0) return List.of();
                add(rules, ids, observation, name, day, from, "23:59:59.999999999", input, cached, output);
            }
        }
        return List.copyOf(rules);
    }

    private static List<ModelPriceCatalog.Rule> openAi(ModelPriceCollectorProcess.Observation observation) {
        var rules = new ArrayList<ModelPriceCatalog.Rule>();
        var ids = new HashSet<String>();
        for (Map<String,Object> table : observation.models()) {
            List<String> columns = strings(table.get("columns"));
            List<String> headings = strings(table.get("sectionHeadings"));
            if (columns == null || !OPENAI_SHORT.equals(columns) || headings == null || !standard(headings)) continue;
            Object rawRows = table.get("rows");
            if (!(rawRows instanceof List<?> rows)) continue;
            for (Object raw : rows) {
                Map<String,Object> row = object(raw);
                String model = (String)row.get("modelLabel");
                Object rawRates = row.get("rates");
                if (!modelName(model) || !(rawRates instanceof List<?> values) || values.size() != 3) continue;
                Long input = number(values.get(0)), output = number(values.get(1)), cached = number(values.get(2));
                if (input == null || output == null || cached == null) continue;
                for (String day : WEEK) add(rules, ids, observation, model, day, null, null, input, cached, output);
            }
        }
        return List.copyOf(rules);
    }

    private static boolean standard(List<String> headings) {
        String title = String.join(" ", headings).toLowerCase(java.util.Locale.ROOT);
        return title.contains("standard") && !title.contains("batch") && !title.contains("flex")
                && !title.contains("priority") && !title.contains("realtime") && !title.contains("fine-tun");
    }

    private static void add(List<ModelPriceCatalog.Rule> rules, Set<String> ids,
                            ModelPriceCollectorProcess.Observation observation, String model, String day,
                            String start, String end, long input, long cached, long output) {
        String scope = observation.provider() + ":" + model + ":default:" + day + ":"
                + (start == null ? "all" : start + "-" + end);
        String id = "rule-" + AuthorityRepository.hash(scope) + "-" + observation.observedMs();
        if (!ids.add(id)) return;
        rules.add(new ModelPriceCatalog.Rule(id, model, "USD", "default", observation.observedMs(), null,
                "UTC", Set.of(day), start, end, Set.of(), Set.of(), null, null,
                input, cached, output, true, null, null, null));
    }

    private static Map<String,Object> object(Object value) {
        if (!(value instanceof Map<?,?> map)) return Map.of();
        var result = new java.util.LinkedHashMap<String,Object>();
        map.forEach((key, item) -> { if (key instanceof String text) result.put(text, item); });
        return result;
    }
    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String))) return null;
        return list.stream().map(String.class::cast).toList();
    }
    private static List<List<String>> windows(Object value) {
        if (!(value instanceof List<?> list)) return null;
        var result = new ArrayList<List<String>>();
        for (Object item : list) {
            List<String> pair = strings(item);
            if (pair == null || pair.size() != 2) return null;
            result.add(pair);
        }
        return result;
    }
    private static Long number(Object value) { return value instanceof Number n && n.longValue() >= 0 ? n.longValue() : null; }
    private static boolean modelName(String value) { return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._/-]{0,199}"); }
    private static boolean time(String value) { return value != null && value.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9](?::[0-5][0-9])?"); }
}
