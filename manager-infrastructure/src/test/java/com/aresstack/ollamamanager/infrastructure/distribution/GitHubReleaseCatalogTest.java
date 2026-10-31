package com.aresstack.ollamamanager.infrastructure.distribution;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;
import com.aresstack.ollamamanager.domain.distribution.OllamaRelease;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class GitHubReleaseCatalogTest {

    private static final String HASH_A = repeat('a');
    private static final String HASH_B = repeat('b');

    private FakeHttpServer server;
    private GitHubReleaseCatalog catalog;

    @Before
    public void setUp() throws Exception {
        server = new FakeHttpServer();
        catalog = new GitHubReleaseCatalog(new HttpTransport(server.resolver(), 2000, 2000),
            GitHubReleaseCatalog.DEFAULT_API);
    }

    @After
    public void tearDown() {
        server.close();
    }

    @Test
    public void readsLatestStableReleaseWithDigestsFromManifest() throws Exception {
        server.text("/repos/ollama/ollama/releases/latest", releaseJson("v0.12.6", false, true));
        server.text("/ollama/ollama/releases/download/v0.12.6/sha256sum.txt",
            HASH_A + "  ./ollama-windows-amd64.zip\n" + HASH_B + " *ollama-windows-amd64-rocm.zip\n");

        OllamaRelease release = catalog.latestStable(CancellationToken.none());

        assertEquals("v0.12.6", release.version());
        assertEquals(HASH_A, release.assetFor(DistributionPackage.BASE).sha256());
        assertEquals(1234L, release.assetFor(DistributionPackage.BASE).sizeBytes());
        assertEquals(HASH_B, release.assetFor(DistributionPackage.ROCM).sha256());
        assertNull(release.assetFor(DistributionPackage.MLX));
    }

    @Test
    public void followsHttpsRedirectsForManifest() throws Exception {
        server.text("/repos/ollama/ollama/releases/tags/v0.12.6", releaseJson("v0.12.6", false, true));
        server.route("/ollama/ollama/releases/download/v0.12.6/sha256sum.txt", exchange -> {
            exchange.getResponseHeaders().add("Location", "https://objects.example/manifest");
            FakeHttpServer.respond(exchange, 302, new byte[0]);
        });
        server.text("/manifest", HASH_A + "  ollama-windows-amd64.zip\n");

        OllamaRelease release = catalog.findRelease("v0.12.6", CancellationToken.none());

        assertEquals(HASH_A, release.assetFor(DistributionPackage.BASE).sha256());
        assertNull(release.assetFor(DistributionPackage.ROCM).sha256());
    }

    @Test
    public void refusesRedirectToPlainHttp() throws Exception {
        server.route("/repos/ollama/ollama/releases/latest", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://evil.example/release");
            FakeHttpServer.respond(exchange, 301, new byte[0]);
        });
        expect(ErrorKind.HTTP_ERROR, () -> catalog.latestStable(CancellationToken.none()));
    }

    @Test
    public void rejectsPrereleasesAndUnknownTags() throws Exception {
        server.text("/repos/ollama/ollama/releases/tags/v0.13.0-rc1", releaseJson("v0.13.0-rc1", true, true));
        expect(ErrorKind.NOT_FOUND, () -> catalog.findRelease("v0.13.0-rc1", CancellationToken.none()));
        expect(ErrorKind.NOT_FOUND, () -> catalog.findRelease("v9.9.9", CancellationToken.none()));
    }

    @Test
    public void leavesDigestsUnknownWithoutManifest() throws Exception {
        server.text("/repos/ollama/ollama/releases/latest", releaseJson("v0.12.6", false, false));
        assertNull(catalog.latestStable(CancellationToken.none()).assetFor(DistributionPackage.BASE).sha256());
    }

    @Test
    public void rejectsMalformedJsonAndManifest() throws Exception {
        server.text("/repos/ollama/ollama/releases/latest", "{\"tag_name\": 'x'}");
        expect(ErrorKind.HTTP_ERROR, () -> catalog.latestStable(CancellationToken.none()));
        server.text("/repos/ollama/ollama/releases/latest", releaseJson("v0.12.6", false, true));
        server.text("/ollama/ollama/releases/download/v0.12.6/sha256sum.txt", "not a digest line\n");
        expect(ErrorKind.HTTP_ERROR, () -> catalog.latestStable(CancellationToken.none()));
    }

    static String releaseJson(String tag, boolean prerelease, boolean withManifest) {
        String base = "https://github.com/ollama/ollama/releases/download/" + tag + "/";
        return "{\"tag_name\":\"" + tag + "\",\"draft\":false,\"prerelease\":" + prerelease + ",\"assets\":["
            + asset(base, "ollama-windows-amd64.zip", 1234) + ","
            + asset(base, "ollama-windows-amd64-rocm.zip", 99) + ","
            + asset(base, "ollama-linux-amd64.tgz", 5)
            + (withManifest ? "," + asset(base, "sha256sum.txt", 300) : "") + "]}";
    }

    private static String asset(String base, String name, long size) {
        return "{\"name\":\"" + name + "\",\"size\":" + size + ",\"browser_download_url\":\"" + base + name + "\"}";
    }

    private static String repeat(char c) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < 64; i++) {
            s.append(c);
        }
        return s.toString();
    }

    interface Call {
        void run() throws Exception;
    }

    static void expect(ErrorKind kind, Call call) throws Exception {
        try {
            call.run();
            fail("expected " + kind);
        } catch (OperationException e) {
            assertEquals(e.getMessage(), kind, e.kind());
        }
    }
}
