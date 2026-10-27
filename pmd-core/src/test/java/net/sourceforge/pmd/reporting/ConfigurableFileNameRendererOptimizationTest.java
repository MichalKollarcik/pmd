/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.reporting;

import static net.sourceforge.pmd.reporting.ConfigurableFileNameRenderer.getDisplayName;
import static net.sourceforge.pmd.util.CollectionUtil.listOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import net.sourceforge.pmd.lang.document.FileId;

class ConfigurableFileNameRendererOptimizationTest {

    private static Path projectRoot() {
        return Paths.get("").toAbsolutePath().getRoot().resolve("ess-profiling-fixture");
    }

    @Test
    void keepsOriginalPathWithoutRoots() {
        FileId file = FileId.fromPath(Paths.get("src", "Example.java"));
        ConfigurableFileNameRenderer renderer = new ConfigurableFileNameRenderer();

        assertEquals(file.getOriginalPath(), renderer.getDisplayName(file));
    }

    @Test
    void choosesShortestPathFromSeveralRoots() {
        Path project = projectRoot();
        FileId file = FileId.fromPath(project.resolve("module/src/pkg/Example.java"));

        assertEquals(Paths.get("pkg", "Example.java").toString(),
            getDisplayName(file, listOf(project, project.resolve("module/test"), project.resolve("module/src"))));
    }

    @Test
    void keepsFirstCandidateWhenSegmentCountsAreEqual() {
        Path path = projectRoot().resolve("src/Example.java");
        FileId file = FileId.fromPath(path);

        assertEquals("Example.java", getDisplayName(file, listOf(path.getParent(), path)));
        assertEquals("", getDisplayName(file, listOf(path, path.getParent())));
    }

    @Test
    void fileSystemRootRestoresAbsolutePath() {
        Path path = projectRoot().resolve("src/Example.java");
        FileId file = FileId.fromPath(path);

        assertEquals(file.getAbsolutePath(), getDisplayName(file, listOf(path.getParent(), path.getRoot())));
    }

    @Test
    void laterRootCanShortenRestoredAbsolutePath() {
        Path path = projectRoot().resolve("src/Example.java");
        FileId file = FileId.fromPath(path);

        assertEquals("Example.java",
            getDisplayName(file, listOf(path.getParent(), path.getRoot(), path.getParent())));
    }

    @Test
    void onlyFileSystemRootKeepsAbsolutePath() {
        Path path = projectRoot().resolve("src/Example.java");
        FileId file = FileId.fromPath(path);

        assertEquals(file.getAbsolutePath(), getDisplayName(file, listOf(path.getRoot())));
    }

    @Test
    void supportsRelativeRoots() {
        FileId file = FileId.fromPath(Paths.get("a", "b", "c", "Example.java"));

        assertEquals(Paths.get("c", "Example.java").toString(),
            getDisplayName(file, listOf(Paths.get("a"), Paths.get("a", "b"))));
    }

    @Test
    void keepsVirtualFileNames() {
        ConfigurableFileNameRenderer renderer = new ConfigurableFileNameRenderer();
        renderer.relativizeWith(projectRoot());
        renderer.relativizeWith(projectRoot().resolve("src"));

        assertEquals(FileId.STDIN.getAbsolutePath(), renderer.getDisplayName(FileId.STDIN));
        assertEquals(FileId.UNKNOWN.getAbsolutePath(), renderer.getDisplayName(FileId.UNKNOWN));
    }

    @Test
    void preservesArchiveParentName() {
        Path project = projectRoot();
        FileId archive = FileId.fromPath(project.resolve("archive.zip"));
        FileId child = FileId.fromAbsolutePath("/pkg/Example.java", archive);
        ConfigurableFileNameRenderer renderer = new ConfigurableFileNameRenderer();
        renderer.relativizeWith(project);

        assertEquals("archive.zip!" + child.getAbsolutePath(), renderer.getDisplayName(child));
    }

    @Test
    void observesRootsAddedBetweenCalls() {
        Path project = projectRoot();
        FileId file = FileId.fromPath(project.resolve("src/pkg/Example.java"));
        ConfigurableFileNameRenderer renderer = new ConfigurableFileNameRenderer();
        renderer.relativizeWith(project);
        assertEquals(Paths.get("src", "pkg", "Example.java").toString(), renderer.getDisplayName(file));

        renderer.relativizeWith(project.resolve("src/pkg"));

        assertEquals("Example.java", renderer.getDisplayName(file));
    }
}
