package com.softility.omivertex.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;

/**
 * Serves the Thymeleaf shell that hosts the React SPA. The shell's script/style tags
 * point at content-hashed filenames read from Vite's build manifest, so a new build
 * produces new URLs (never stale) while the hashed assets themselves can be cached
 * long-term. The shell itself is served no-store so it always resolves the latest hash.
 */
@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);
    private static final String MANIFEST = "static/.vite/manifest.json";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private volatile Assets assets;

    private record Assets(String js, List<String> css) {}

    @GetMapping("/")
    public String index(Model model, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store, must-revalidate");
        Assets a = resolveAssets();
        model.addAttribute("jsFile", a.js());
        model.addAttribute("cssFiles", a.css());
        return "index";
    }

    /** Reads and caches the entry JS + CSS from the Vite manifest (falls back if absent). */
    private Assets resolveAssets() {
        Assets cached = assets;
        if (cached != null) {
            return cached;
        }
        Assets resolved = readManifest();
        assets = resolved;
        return resolved;
    }

    private Assets readManifest() {
        ClassPathResource resource = new ClassPathResource(MANIFEST);
        if (!resource.exists()) {
            log.warn("Vite manifest not found at classpath:{} — build the frontend (npm run build). "
                    + "Falling back to legacy asset names.", MANIFEST);
            return new Assets("/assets/app.js", List.of("/assets/app.css"));
        }
        try {
            JsonNode root = objectMapper.readTree(resource.getInputStream());
            JsonNode entry = null;
            for (JsonNode node : root) {
                if (node.path("isEntry").asBoolean(false)) {
                    entry = node;
                    break;
                }
            }
            if (entry == null) {
                throw new IllegalStateException("no isEntry chunk in manifest");
            }
            String js = "/" + entry.path("file").asText();
            List<String> css = new ArrayList<>();
            entry.path("css").forEach(c -> css.add("/" + c.asText()));
            return new Assets(js, List.copyOf(css));
        } catch (Exception e) {
            log.error("Failed to read Vite manifest; falling back to legacy asset names", e);
            return new Assets("/assets/app.js", List.of("/assets/app.css"));
        }
    }
}
