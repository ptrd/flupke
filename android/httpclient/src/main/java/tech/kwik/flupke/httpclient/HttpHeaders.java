package tech.kwik.flupke.httpclient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

public class HttpHeaders {

    private Map<String, List<String>> headerMap;

    public HttpHeaders(Map<String, List<String>> headerMap) {
        this.headerMap = headerMap;
    }

    public HttpHeaders() {
        this.headerMap = new HashMap<>();
    }

    public static HttpHeaders of(Map<String,List<String>> headerMap, BiPredicate<String,String> filter) {
        return new HttpHeaders(headerMap.entrySet().stream()
                .map(entry -> filterEntryValues(entry, filter))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    private static Optional<Map.Entry<String, List<String>>> filterEntryValues(Map.Entry<String, List<String>> entry, BiPredicate<String,String> filter) {
        List<String> filteredValues = entry.getValue().stream()
                .filter(value -> filter.test(entry.getKey(), value))
                .collect(Collectors.toList());
        return filteredValues.isEmpty()? Optional.empty(): Optional.of(Map.entry(entry.getKey(), filteredValues));
    }

    public Map<String,List<String>> map() {
        return headerMap;
    }
}
