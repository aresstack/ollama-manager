package com.aresstack.ollamamanager.infrastructure.catalog;

import com.aresstack.ollamamanager.application.catalog.CatalogModel;
import com.aresstack.ollamamanager.application.catalog.CatalogSource;
import com.aresstack.ollamamanager.application.catalog.CatalogVariant;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import jodd.jerry.Jerry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ollama.com's server-rendered search and model pages. Ported from askai-java8. Selection relies on the
 * stable {@code /library/} hrefs and visible text, not on Tailwind class names, which change often. A non-trivial
 * page without any hit fails loudly, so scraper drift does not look like "no matches".
 */
final class OllamaLibraryHtmlParser {

    private static final String LIBRARY_PREFIX = "/library/";
    private static final Pattern PARAM_SIZE = Pattern.compile("(?i)^\\d+(?:\\.\\d+)?x?\\d*[bm]$");
    private static final Pattern CAPABILITY = Pattern.compile("^[a-z][a-z-]{2,15}$");
    private static final Pattern PULLS = Pattern.compile("([\\d.,]+[KMB]?)\\s*Pulls");
    private static final Pattern UPDATED = Pattern.compile("Updated\\s+(.+?)\\s*$");
    private static final Pattern SIZE = Pattern.compile("(?i)^\\d+(?:\\.\\d+)?(?:gb|mb|kb|tb|b)$");
    private static final int MIN_MEANINGFUL_HTML = 200;

    List<CatalogModel> parseSearchResults(String html) throws OperationException {
        List<CatalogModel> models = new ArrayList<CatalogModel>();
        Jerry doc = Jerry.of(html == null ? "" : html);
        doc.find("a[href^=\"" + LIBRARY_PREFIX + "\"]").each((node, index) -> {
            String href = node.attr("href");
            String base = href == null ? "" : href.substring(LIBRARY_PREFIX.length());
            // Only real results: no tag, no sub path, and a heading present.
            if (!base.isEmpty() && base.indexOf(':') < 0 && base.indexOf('/') < 0 && node.find("h2").size() > 0) {
                models.add(searchModel(base, node));
            }
            return true;
        });
        if (models.isEmpty() && html != null && html.length() > MIN_MEANINGFUL_HTML
            && !html.contains("No models found")) {
            throw new OperationException(ErrorKind.HTTP_ERROR,
                "the ollama.com search page changed (no results found in the HTML)");
        }
        return models;
    }

    private static CatalogModel searchModel(String base, Jerry node) {
        String description = node.find("p").size() > 0 ? node.find("p").first().text().trim() : "";
        List<String> capabilities = new ArrayList<String>();
        List<String> sizes = new ArrayList<String>();
        node.find("span").each((span, index) -> {
            String text = span.text().trim();
            String lower = text.toLowerCase(Locale.ROOT);
            if (PARAM_SIZE.matcher(lower).matches()) {
                if (!sizes.contains(lower)) {
                    sizes.add(lower);
                }
            } else if (CAPABILITY.matcher(lower).matches() && !isStatLabel(lower) && !capabilities.contains(lower)) {
                capabilities.add(lower);
            }
            return true;
        });
        String blockText = normalize(node.text());
        return new CatalogModel(CatalogSource.OLLAMA_LIBRARY, base, description, capabilities, sizes,
            firstGroup(PULLS, blockText), firstGroup(UPDATED, blockText));
    }

    private static boolean isStatLabel(String lower) {
        return lower.equals("pulls") || lower.equals("tags") || lower.equals("updated");
    }

    /**
     * Parse {@code /library/<baseName>/tags} or {@code /library/<baseName>} into its variants. ollama.com renders
     * each tag twice (mobile and desktop row); only the row with the {@code ·}-separated detail line is used.
     */
    List<CatalogVariant> parseVariants(String baseName, String html) throws OperationException {
        Jerry doc = Jerry.of(html == null ? "" : html);
        Map<String, CatalogVariant> variants = new LinkedHashMap<String, CatalogVariant>();
        doc.find("a[href^=\"" + LIBRARY_PREFIX + baseName + ":\"]").each((node, index) -> {
            String tag = node.attr("href").substring(LIBRARY_PREFIX.length());
            String text = normalize(node.text());
            if (text.indexOf('·') >= 0 && !isCloud(tag) && !variants.containsKey(tag)) {
                variants.put(tag, variant(tag, node, text));
            }
            return true;
        });
        // Tags listed only as copy commands, without a detail row.
        doc.find("input.command").each((node, index) -> {
            String value = node.attr("value");
            if (value != null && value.startsWith(baseName + ":") && !isCloud(value) && !variants.containsKey(value)) {
                variants.put(value, new CatalogVariant(value, "", "", null, false));
            }
            return true;
        });
        if (variants.isEmpty() && html != null && html.length() > MIN_MEANINGFUL_HTML) {
            throw new OperationException(ErrorKind.HTTP_ERROR,
                "the ollama.com model page changed (no variants found in the HTML)");
        }
        return new ArrayList<CatalogVariant>(variants.values());
    }

    /** Parse a detail row: "... 15GB · 128K context window · Text, Image · 7 months ago". */
    private static CatalogVariant variant(String tag, Jerry node, String text) {
        String[] parts = text.split("·");
        String sizeToken = lastToken(parts[0]);
        String size = SIZE.matcher(sizeToken).matches() ? sizeToken : "";
        String context = parts.length > 1 ? parts[1].trim() : "";
        List<String> inputs = new ArrayList<String>();
        if (parts.length > 2) {
            for (String input : parts[2].split(",")) {
                if (!input.trim().isEmpty()) {
                    inputs.add(input.trim());
                }
            }
        }
        boolean[] latest = {false};
        node.find("span").each((span, index) -> {
            if ("latest".equalsIgnoreCase(span.text().trim())) {
                latest[0] = true;
            }
            return true;
        });
        return new CatalogVariant(tag, size, context, inputs, latest[0]);
    }

    /** Cloud tags run on ollama.com and have nothing to download, so they are no local install option. */
    static boolean isCloud(String tag) {
        return tag.endsWith("-cloud") || tag.endsWith(":cloud");
    }

    private static String lastToken(String text) {
        String trimmed = text.trim();
        int space = trimmed.lastIndexOf(' ');
        return space >= 0 ? trimmed.substring(space + 1) : trimmed;
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replace(' ', ' ').replaceAll("\\s+", " ").trim();
    }
}
