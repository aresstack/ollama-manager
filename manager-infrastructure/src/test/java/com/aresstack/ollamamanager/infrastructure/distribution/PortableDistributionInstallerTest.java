package com.aresstack.ollamamanager.infrastructure.distribution;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.ProgressListener;
import com.aresstack.ollamamanager.application.common.TransferProgress;
import com.aresstack.ollamamanager.domain.distribution.DistributionPackage;
import com.aresstack.ollamamanager.domain.distribution.OllamaRelease;
import com.aresstack.ollamamanager.domain.distribution.ReleaseAsset;
import com.aresstack.ollamamanager.domain.server.Endpoint;
import com.aresstack.ollamamanager.domain.server.InstallationKind;
import com.aresstack.ollamamanager.domain.server.OllamaInstallation;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.aresstack.ollamamanager.infrastructure.distribution.GitHubReleaseCatalogTest.expect;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class PortableDistributionInstallerTest {

    private static final Platform WINDOWS = new Platform("Windows 11", "amd64");
    private static final Set<DistributionPackage> BASE = EnumSet.of(DistributionPackage.BASE);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private FakeHttpServer server;
    private File root;
    private final AtomicLong freeBytes = new AtomicLong(Long.MAX_VALUE);

    @Before
    public void setUp() throws Exception {
        server = new FakeHttpServer();
        root = folder.newFolder("portable");
    }

    @After
    public void tearDown() {
        server.close();
    }

    @Test
    public void installsVerifiesAndActivatesBaseDistribution() throws Exception {
        OllamaRelease release = publish("v0.12.6", base("v0.12.6"));
        List<TransferProgress> progress = new ArrayList<TransferProgress>();

        OllamaInstallation installation = installer(WINDOWS).install(release, BASE, root, progress::add,
            CancellationToken.none());

        assertEquals(InstallationKind.MANAGED_PORTABLE, installation.kind());
        assertEquals("v0.12.6", installation.version());
        assertEquals(new File(root, "versions/v0.12.6").getAbsoluteFile(), installation.binaryDirectory());
        assertEquals(Endpoint.DEFAULT, installation.endpoint());
        assertArrayEquals(bytes("exe v0.12.6"), read(new File(installation.binaryDirectory(), "ollama.exe")));
        assertTrue(new File(installation.binaryDirectory(), "lib/ollama/ggml-base.dll").isFile());
        String notice = new String(read(new File(installation.binaryDirectory(), "OLLAMA-NOTICE.txt")),
            StandardCharsets.UTF_8);
        assertTrue(notice.contains("MIT License"));
        assertTrue(notice.contains(release.assetFor(DistributionPackage.BASE).sha256()));
        assertEquals(Arrays.asList(installation), installer(WINDOWS).installedVersions(root));
        assertEquals(0, new File(root, "downloads").list().length);
        assertEquals(0, new File(root, "staging").list().length);
        assertTrue(progress.get(progress.size() - 1).phase().startsWith("activating"));
    }

    @Test
    public void overlaysRocmAddOnOfSameVersion() throws Exception {
        byte[] rocm = zip("lib/ollama/rocm/hip.dll", "hip", "lib/ollama/ggml-base.dll", "rocm build");
        OllamaRelease release = publish("v0.12.6", base("v0.12.6"), "ollama-windows-amd64-rocm.zip", rocm);

        OllamaInstallation installation = installer(WINDOWS).install(release,
            EnumSet.of(DistributionPackage.BASE, DistributionPackage.ROCM), root, ignore(), CancellationToken.none());

        assertEquals("v0.12.6+rocm", installation.binaryDirectory().getName());
        assertTrue(new File(installation.binaryDirectory(), "lib/ollama/rocm/hip.dll").isFile());
        assertArrayEquals(bytes("rocm build"),
            read(new File(installation.binaryDirectory(), "lib/ollama/ggml-base.dll")));
    }

    @Test
    public void rejectsAddOnFromAnotherReleaseVersion() throws Exception {
        OllamaRelease release = publish("v0.12.6", base("v0.12.6"));
        byte[] rocm = zip("lib/ollama/rocm/hip.dll", "hip");
        server.bytes("/ollama/ollama/releases/download/v0.12.5/ollama-windows-amd64-rocm.zip", rocm);
        List<ReleaseAsset> assets = new ArrayList<ReleaseAsset>(release.assets());
        assets.add(new ReleaseAsset("ollama-windows-amd64-rocm.zip",
            "https://github.com/ollama/ollama/releases/download/v0.12.5/ollama-windows-amd64-rocm.zip",
            rocm.length, sha(rocm)));
        OllamaRelease mixed = new OllamaRelease("v0.12.6", assets);

        expect(ErrorKind.INVALID_STATE, () -> installer(WINDOWS).install(mixed,
            EnumSet.of(DistributionPackage.BASE, DistributionPackage.ROCM), root, ignore(), CancellationToken.none()));
        assertTrue(server.requests.isEmpty());
    }

    @Test
    public void rejectsWrongChecksumAndDeletesDownload() throws Exception {
        byte[] zip = base("v0.12.6");
        OllamaRelease release = publish("v0.12.6", zip);
        OllamaRelease tampered = new OllamaRelease("v0.12.6", Arrays.asList(
            release.assetFor(DistributionPackage.BASE).withSha256(sha("other".getBytes(StandardCharsets.UTF_8)))));

        expect(ErrorKind.CHECKSUM_MISMATCH,
            () -> installer(WINDOWS).install(tampered, BASE, root, ignore(), CancellationToken.none()));

        assertEquals(0, new File(root, "downloads").list().length);
        assertEquals(0, new File(root, "versions").list().length);
        assertNull(installer(WINDOWS).active(root));
    }

    @Test
    public void rejectsMissingAssetAndMissingChecksum() throws Exception {
        OllamaRelease empty = new OllamaRelease("v0.12.6", new ArrayList<ReleaseAsset>());
        expect(ErrorKind.NOT_FOUND, () -> installer(WINDOWS).install(empty, BASE, root, ignore(),
            CancellationToken.none()));
        OllamaRelease unverified = new OllamaRelease("v0.12.6", Arrays.asList(new ReleaseAsset(
            "ollama-windows-amd64.zip", url("v0.12.6", "ollama-windows-amd64.zip"), 10, null)));
        expect(ErrorKind.CHECKSUM_MISMATCH, () -> installer(WINDOWS).install(unverified, BASE, root, ignore(),
            CancellationToken.none()));
        expect(ErrorKind.INVALID_CONFIGURATION, () -> installer(WINDOWS).install(unverified,
            EnumSet.of(DistributionPackage.ROCM), root, ignore(), CancellationToken.none()));
    }

    @Test
    public void rejectsPathTraversalAndAbsoluteEntries() throws Exception {
        for (String evil : new String[] {"../escape.txt", "lib/../../escape.txt", "/abs.txt", "C:/win.txt",
            "lib\\..\\..\\escape.txt", "ollama.exe:stream"}) {
            File target = folder.newFolder();
            OllamaRelease release = publish("v1", zip("ollama.exe", "exe", evil, "boom"));
            expect(ErrorKind.INVALID_ARCHIVE, () -> installer(WINDOWS).install(release, BASE, target, ignore(),
                CancellationToken.none()));
            assertFalse(new File(target.getParentFile(), "escape.txt").exists());
            assertEquals(0, new File(target, "versions").list().length);
            assertEquals(0, new File(target, "staging").list().length);
        }
    }

    @Test
    public void rejectsDuplicateEntriesAndArchivesWithoutExecutable() throws Exception {
        OllamaRelease duplicate = publish("v1", zip("ollama.exe", "a", "OLLAMA.EXE", "b"));
        expect(ErrorKind.INVALID_ARCHIVE, () -> installer(WINDOWS).install(duplicate, BASE, root, ignore(),
            CancellationToken.none()));
        OllamaRelease noExe = publish("v2", zip("bin/ollama.exe", "nested"));
        expect(ErrorKind.INVALID_ARCHIVE, () -> installer(WINDOWS).install(noExe, BASE, root, ignore(),
            CancellationToken.none()));
        OllamaRelease garbage = publish("v3", bytes("not a zip at all"));
        expect(ErrorKind.INVALID_ARCHIVE, () -> installer(WINDOWS).install(garbage, BASE, root, ignore(),
            CancellationToken.none()));
    }

    @Test
    public void failsEarlyWhenDiskIsFull() throws Exception {
        OllamaRelease release = publish("v0.12.6", base("v0.12.6"));
        freeBytes.set(10);
        expect(ErrorKind.INSUFFICIENT_DISK, () -> installer(WINDOWS).install(release, BASE, root, ignore(),
            CancellationToken.none()));
        assertTrue(server.requests.isEmpty());
    }

    @Test
    public void resumesTruncatedDownloadOnRetry() throws Exception {
        byte[] zip = base("v0.12.6");
        OllamaRelease release = publish("v0.12.6", zip);
        String path = "/ollama/ollama/releases/download/v0.12.6/ollama-windows-amd64.zip";
        int cut = zip.length / 2;
        server.route(path, exchange -> {
            exchange.sendResponseHeaders(200, zip.length);
            OutputStream out = exchange.getResponseBody();
            out.write(zip, 0, cut);
            out.flush();
        });

        expect(ErrorKind.CONNECTION_FAILED, () -> installer(WINDOWS).install(release, BASE, root, ignore(),
            CancellationToken.none()));
        assertNull(installer(WINDOWS).active(root));

        server.route(path, exchange -> {
            String range = exchange.getRequestHeaders().getFirst("Range");
            int from = Integer.parseInt(range.substring("bytes=".length(), range.length() - 1));
            exchange.getResponseHeaders().add("Content-Range", "bytes " + from + "-" + (zip.length - 1) + "/"
                + zip.length);
            FakeHttpServer.respond(exchange, 206, Arrays.copyOfRange(zip, from, zip.length));
        });
        OllamaInstallation installation = installer(WINDOWS).install(release, BASE, root, ignore(),
            CancellationToken.none());

        assertEquals(Arrays.asList("bytes=" + cut + "-"), server.rangeHeaders);
        assertArrayEquals(bytes("exe v0.12.6"), read(new File(installation.binaryDirectory(), "ollama.exe")));
    }

    @Test
    public void restartsWhenServerIgnoresRange() throws Exception {
        byte[] zip = base("v0.12.6");
        OllamaRelease release = publish("v0.12.6", zip);
        File downloads = new File(root, "downloads");
        downloads.mkdirs();
        Files.write(new File(downloads, release.assetFor(DistributionPackage.BASE).sha256()
            + "-ollama-windows-amd64.zip.part").toPath(), bytes("garbage"));

        OllamaInstallation installation = installer(WINDOWS).install(release, BASE, root, ignore(),
            CancellationToken.none());

        assertEquals(1, server.rangeHeaders.size());
        assertTrue(new File(installation.binaryDirectory(), "ollama.exe").isFile());
    }

    @Test
    public void verifiesCompletePartialLocallyInsteadOfRequestingRange() throws Exception {
        byte[] zip = base("v0.12.6");
        OllamaRelease release = publish("v0.12.6", zip);
        File downloads = new File(root, "downloads");
        downloads.mkdirs();
        Files.write(new File(downloads, release.assetFor(DistributionPackage.BASE).sha256()
            + "-ollama-windows-amd64.zip.part").toPath(), zip);

        OllamaInstallation installation = installer(WINDOWS).install(release, BASE, root, ignore(),
            CancellationToken.none());

        assertTrue(server.requests.isEmpty());
        assertTrue(new File(installation.binaryDirectory(), "ollama.exe").isFile());
    }

    @Test
    public void usesCanonicalDirectoryForActiveProtection() throws Exception {
        PortableDistributionInstaller installer = installer(WINDOWS);
        OllamaInstallation current = installer.install(publish("v0.12.6", base("v0.12.6")), BASE, root, ignore(),
            CancellationToken.none());
        OllamaInstallation dotted = new OllamaInstallation(InstallationKind.MANAGED_PORTABLE, "v0.12.6",
            new File(current.binaryDirectory(), "."), Endpoint.DEFAULT);

        expect(ErrorKind.INVALID_STATE, () -> installer.uninstall(dotted, root));
        installer.activate(dotted, root);
        assertEquals(current, installer.active(root));
    }

    @Test
    public void cancellationRemovesPartialResults() throws Exception {
        byte[] zip = base("v0.12.6");
        OllamaRelease release = publish("v0.12.6", zip);
        CancellationToken token = new CancellationToken();
        server.route("/ollama/ollama/releases/download/v0.12.6/ollama-windows-amd64.zip", exchange -> {
            exchange.sendResponseHeaders(200, zip.length);
            OutputStream out = exchange.getResponseBody();
            out.write(zip, 0, 10);
            out.flush();
            token.cancel();
            out.write(zip, 10, zip.length - 10);
        });

        expect(ErrorKind.CANCELLED, () -> installer(WINDOWS).install(release, BASE, root, ignore(), token));

        assertEquals(0, new File(root, "downloads").list().length);
        assertEquals(0, new File(root, "staging").list().length);
        assertEquals(0, new File(root, "versions").list().length);
    }

    @Test
    public void failedUpdateKeepsPreviousVersionActiveAndAllowsRollback() throws Exception {
        PortableDistributionInstaller installer = installer(WINDOWS);
        OllamaInstallation old = installer.install(publish("v0.12.5", base("v0.12.5")), BASE, root, ignore(),
            CancellationToken.none());

        OllamaRelease broken = publish("v0.12.6", zip("README.txt", "no exe"));
        expect(ErrorKind.INVALID_ARCHIVE, () -> installer.install(broken, BASE, root, ignore(),
            CancellationToken.none()));
        assertEquals(old, installer.active(root));

        OllamaInstallation updated = installer.install(publish("v0.12.7", base("v0.12.7")), BASE, root, ignore(),
            CancellationToken.none());
        assertEquals(updated, installer.active(root));
        assertEquals(Arrays.asList(updated, old), installer.installedVersions(root));

        installer.activate(old, root);
        assertEquals(old, installer.active(root));
        assertTrue(new File(updated.binaryDirectory(), "ollama.exe").isFile());
        expect(ErrorKind.INVALID_STATE, () -> installer.install(publish("v0.12.5", base("v0.12.5")), BASE, root,
            ignore(), CancellationToken.none()));
    }

    @Test
    public void uninstallRemovesOnlyManagerFilesAndNeverTheActiveVersion() throws Exception {
        PortableDistributionInstaller installer = installer(WINDOWS);
        OllamaInstallation old = installer.install(publish("v0.12.5", base("v0.12.5")), BASE, root, ignore(),
            CancellationToken.none());
        OllamaInstallation current = installer.install(publish("v0.12.6", base("v0.12.6")), BASE, root, ignore(),
            CancellationToken.none());
        File models = new File(old.binaryDirectory(), "models/blobs/sha256-x");
        models.getParentFile().mkdirs();
        Files.write(models.toPath(), bytes("model"));

        expect(ErrorKind.INVALID_STATE, () -> installer.uninstall(current, root));
        installer.uninstall(old, root);

        assertTrue(models.isFile());
        assertFalse(new File(old.binaryDirectory(), "ollama.exe").exists());
        assertFalse(new File(old.binaryDirectory(), "lib").exists());
        assertEquals(Arrays.asList(current), installer.installedVersions(root));
    }

    @Test
    public void refusesForeignInstallations() throws Exception {
        File foreign = folder.newFolder("Ollama");
        expect(ErrorKind.NOT_OWNED, () -> installer(WINDOWS).uninstall(new OllamaInstallation(
            InstallationKind.MANAGED_PORTABLE, "v1", foreign, Endpoint.DEFAULT), root));
        expect(ErrorKind.NOT_OWNED, () -> installer(WINDOWS).uninstall(
            OllamaInstallation.external("v1", Endpoint.DEFAULT), root));
    }

    @Test
    public void rejectsUnsupportedPlatforms() throws Exception {
        OllamaRelease release = publish("v0.12.6", base("v0.12.6"));
        for (Platform platform : new Platform[] {new Platform("Linux", "amd64"), new Platform("Windows 11", "aarch64"),
            new Platform("Mac OS X", "x86_64")}) {
            expect(ErrorKind.UNSUPPORTED_PLATFORM, () -> installer(platform).install(release, BASE, root, ignore(),
                CancellationToken.none()));
        }
        assertTrue(server.requests.isEmpty());
    }

    private PortableDistributionInstaller installer(Platform platform) {
        return new PortableDistributionInstaller(new HttpTransport(server.resolver(), 2000, 2000), platform,
            directory -> freeBytes.get());
    }

    /** Serve the given name/content pairs for a tag and return the release with real digests. */
    private OllamaRelease publish(String tag, byte[] baseZip, Object... more) {
        List<ReleaseAsset> assets = new ArrayList<ReleaseAsset>();
        assets.add(serve(tag, "ollama-windows-amd64.zip", baseZip));
        for (int i = 0; i + 1 < more.length; i += 2) {
            assets.add(serve(tag, (String) more[i], (byte[]) more[i + 1]));
        }
        return new OllamaRelease(tag, assets);
    }

    private ReleaseAsset serve(String tag, String name, byte[] content) {
        server.bytes("/ollama/ollama/releases/download/" + tag + "/" + name, content);
        return new ReleaseAsset(name, url(tag, name), content.length, sha(content));
    }

    private static String url(String tag, String name) {
        return "https://github.com/ollama/ollama/releases/download/" + tag + "/" + name;
    }

    private static byte[] base(String tag) throws IOException {
        return zip("ollama.exe", "exe " + tag, "lib/ollama/ggml-base.dll", "base " + tag);
    }

    static byte[] zip(String... namesAndContents) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(bytes);
        for (int i = 0; i + 1 < namesAndContents.length; i += 2) {
            zip.putNextEntry(new ZipEntry(namesAndContents[i]));
            zip.write(bytes(namesAndContents[i + 1]));
            zip.closeEntry();
        }
        zip.close();
        return bytes.toByteArray();
    }

    private static String sha(byte[] content) {
        try {
            File temp = File.createTempFile("sha", ".bin");
            try {
                Files.write(temp.toPath(), content);
                return PortableDistributionInstaller.sha256(temp, CancellationToken.none());
            } finally {
                temp.delete();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] read(File file) throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    private static ProgressListener<TransferProgress> ignore() {
        return ProgressListener.ignoring();
    }
}
