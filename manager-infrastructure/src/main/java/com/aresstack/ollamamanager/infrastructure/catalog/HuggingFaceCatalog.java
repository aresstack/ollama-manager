package com.aresstack.ollamamanager.infrastructure.catalog;

import com.aresstack.ollamamanager.application.catalog.CatalogModel;
import com.aresstack.ollamamanager.application.catalog.CatalogSource;
import com.aresstack.ollamamanager.application.catalog.CatalogVariant;
import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.application.port.ModelCatalogPort;
import com.aresstack.ollamamanager.infrastructure.distribution.HttpTransport;
import com.aresstack.ollamamanager.infrastructure.json.Json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Searches GGUF repositories on huggingface.co. Each quantization becomes a variant with the pull name
 * {@code hf.co/<owner>/<repo>:<QUANT>}, which Ollama downloads and registers itself (including the chat template
 * from the GGUF), so the same install works on owned and existing servers.
 */
public final class HuggingFaceCatalog implements ModelCatalogPort {

    public static final String DEFAULT_BASE = "https://huggingface.co";
    static final int SEARCH_LIMIT = 40;
    private static final int MAX_JSON_BYTES = 8 * 1024 * 1024;
    private static final Pattern REPO_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*/[A-Za-z0-9][A-Za-z0-9._-]*");
    /** Quantization label inside a GGUF file name, for example Q4_K_M, IQ3_XXS, Q8_0, BF16. */
    private static final Pattern QUANT = Pattern.compile(
        "(?i)(?:^|[-_.])((?:UD-)?(?:I?Q[1-8](?:_[A-Z0-9]+)*|BF16|F16|F32|MXFP4(?:_MOE)?))(?=\\.gguf$|[-_.])");
    private static final Pattern SHARD = Pattern.compile("(?i)-(\\d{5})-of-\\d{5}\\.gguf$");
    private static final Map<String, String> HEADERS = Collections.singletonMap("Accept", "application/json");

    private final HttpTransport http;
    private final String base;

    public HuggingFaceCatalog(HttpTransport http, String base) {
        this.http = http;
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    public static HuggingFaceCatalog defaults() {
        return new HuggingFaceCatalog(HttpTransport.defaults(), DEFAULT_BASE);
    }

    @Override
    public CatalogSource source() {
        return CatalogSource.HUGGING_FACE;
    }

    @Override
    public List<CatalogModel> search(String query, CancellationToken cancellation) throws OperationException {
        String url = base + "/api/models?filter=gguf&sort=downloads&direction=-1&limit=" + SEARCH_LIMIT
            + "&search=" + Urls.encode(query);
        List<CatalogModel> models = new ArrayList<CatalogModel>();
        for (Object item : list(fetch(url, cancellation), url)) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> repo = (Map<?, ?>) item;
            Object id = repo.containsKey("id") ? repo.get("id") : repo.get("modelId");
            if (!(id instanceof String) || !REPO_ID.matcher((String) id).matches()) {
                continue;
            }
            Object task = repo.get("pipeline_tag");
            List<String> capabilities = task instanceof String
                ? Collections.singletonList((String) task) : Collections.<String>emptyList();
            models.add(new CatalogModel(CatalogSource.HUGGING_FACE, (String) id, "", capabilities, null,
                count(repo.get("downloads")), date(repo.get("lastModified"))));
        }
        return models;
    }

    @Override
    public List<CatalogVariant> variants(String modelId, CancellationToken cancellation) throws OperationException {
        if (modelId == null || !REPO_ID.matcher(modelId.trim()).matches()) {
            throw new OperationException(ErrorKind.INVALID_CONFIGURATION, "invalid Hugging Face repository: " + modelId);
        }
        String repo = modelId.trim();
        String url = base + "/api/models/" + Urls.encodePath(repo) + "/tree/main?recursive=true";
        Map<String, long[]> sizes = new LinkedHashMap<String, long[]>();
        for (Object item : list(fetch(url, cancellation), url)) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<?, ?> file = (Map<?, ?>) item;
            Object path = file.get("path");
            if (!"file".equals(file.get("type")) || !(path instanceof String)) {
                continue;
            }
            String quant = quantization((String) path);
            if (quant == null) {
                continue;
            }
            long[] total = sizes.get(quant);
            if (total == null) {
                total = new long[1];
                sizes.put(quant, total);
            }
            total[0] += size(file);
        }
        List<CatalogVariant> variants = new ArrayList<CatalogVariant>();
        for (Map.Entry<String, long[]> entry : sizes.entrySet()) {
            String quant = entry.getKey();
            variants.add(new CatalogVariant("hf.co/" + repo + ":" + quant, bytes(entry.getValue()[0]), "", null,
                "Q4_K_M".equals(quant)));
        }
        return variants;
    }

    /**
     * Return the quantization of a GGUF model file, or {@code null} for non-GGUF files and multimodal projector
     * files ({@code mmproj}), which Ollama fetches together with the model and are no variant of their own.
     */
    static String quantization(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".gguf") || lower.contains("mmproj")) {
            return null;
        }
        Matcher shard = SHARD.matcher(name);
        String stem = shard.find() ? name.substring(0, shard.start()) + ".gguf" : name;
        Matcher matcher = QUANT.matcher(stem);
        String found = null;
        while (matcher.find()) {
            found = matcher.group(1);
        }
        return found == null ? null : found.toUpperCase(Locale.ROOT);
    }

    private String fetch(String url, CancellationToken cancellation) throws OperationException {
        return http.getText(url, HEADERS, MAX_JSON_BYTES, cancellation);
    }

    private static List<?> list(String body, String url) throws OperationException {
        Object parsed;
        try {
            parsed = Json.parse(body);
        } catch (IllegalArgumentException e) {
            throw new OperationException(ErrorKind.HTTP_ERROR, "invalid JSON from " + url, e);
        }
        if (!(parsed instanceof List)) {
            throw new OperationException(ErrorKind.HTTP_ERROR, "unexpected JSON from " + url);
        }
        return (List<?>) parsed;
    }

    private static long size(Map<?, ?> file) {
        Object lfs = file.get("lfs");
        Object value = lfs instanceof Map && ((Map<?, ?>) lfs).get("size") instanceof Number
            ? ((Map<?, ?>) lfs).get("size") : file.get("size");
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    static String bytes(long value) {
        if (value <= 0) {
            return "";
        }
        double gb = value / 1e9;
        return gb >= 1 ? String.format(Locale.ROOT, "%.1fGB", gb)
            : String.format(Locale.ROOT, "%.0fMB", value / 1e6);
    }

    private static String count(Object value) {
        if (!(value instanceof Number)) {
            return "";
        }
        long n = ((Number) value).longValue();
        if (n >= 1000000) {
            return String.format(Locale.ROOT, "%.1fM", n / 1e6);
        }
        return n >= 1000 ? String.format(Locale.ROOT, "%.1fK", n / 1e3) : Long.toString(n);
    }

    private static String date(Object value) {
        return value instanceof String && ((String) value).length() >= 10 ? ((String) value).substring(0, 10) : "";
    }
}
