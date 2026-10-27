/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

import net.sourceforge.pmd.internal.util.IOUtil;

class IOUtilCoverageTest {

    @TempDir
    Path tempDir;

    @Test
    void closeAllAcceptsEmptyCollection() {
        assertNull(IOUtil.closeAll(Collections.emptyList()));
    }

    @Test
    void closeAllClosesResourcesInOrder() throws Exception {
        AutoCloseable first = mock(AutoCloseable.class);
        AutoCloseable second = mock(AutoCloseable.class);

        assertNull(IOUtil.closeAll(Arrays.asList(first, second)));

        InOrder order = inOrder(first, second);
        order.verify(first).close();
        order.verify(second).close();
        order.verifyNoMoreInteractions();
    }

    @Test
    void closeAllContinuesAfterFailure() throws Exception {
        AutoCloseable first = mock(AutoCloseable.class);
        AutoCloseable second = mock(AutoCloseable.class);
        IOException failure = new IOException("Cannot close first resource");
        doThrow(failure).when(first).close();

        Exception result = IOUtil.closeAll(Arrays.asList(first, second));

        assertSame(failure, result);
        assertEquals(0, result.getSuppressed().length);
        InOrder order = inOrder(first, second);
        order.verify(first).close();
        order.verify(second).close();
        order.verifyNoMoreInteractions();
    }

    @Test
    void closeAllPreservesSuppressedExceptionsInOrder() throws Exception {
        AutoCloseable first = mock(AutoCloseable.class);
        AutoCloseable second = mock(AutoCloseable.class);
        AutoCloseable third = mock(AutoCloseable.class);
        IOException firstFailure = new IOException("first");
        IOException secondFailure = new IOException("second");
        IllegalStateException thirdFailure = new IllegalStateException("third");
        doThrow(firstFailure).when(first).close();
        doThrow(secondFailure).when(second).close();
        doThrow(thirdFailure).when(third).close();

        Exception result = IOUtil.closeAll(Arrays.asList(first, second, third));

        assertSame(firstFailure, result);
        assertArrayEquals(new Throwable[] {secondFailure, thirdFailure}, result.getSuppressed());
        verify(first).close();
        verify(second).close();
        verify(third).close();
    }

    @Test
    void ensureClosedClosesResourceWithoutPendingException() throws Exception {
        AutoCloseable resource = mock(AutoCloseable.class);

        assertDoesNotThrow(() -> IOUtil.ensureClosed(Collections.singletonList(resource), null));

        verify(resource).close();
    }

    @Test
    void ensureClosedClosesResourceBeforeRethrowingPendingException() throws Exception {
        AutoCloseable resource = mock(AutoCloseable.class);
        IOException pending = new IOException("Original operation failed");

        IOException actual = assertThrows(IOException.class,
            () -> IOUtil.ensureClosed(Collections.singletonList(resource), pending));

        assertSame(pending, actual);
        verify(resource).close();
    }

    @Test
    void createWriterCreatesParentDirectoriesAndWritesUtf8() throws IOException {
        Path destination = tempDir.resolve("reports/nested/result.txt");
        String content = "Pr\u00edli\u0161 \u017eltu\u010dk\u00fd k\u00f4\u0148";

        try (Writer writer = IOUtil.createWriter(StandardCharsets.UTF_8, destination.toString())) {
            writer.write(content);
        }

        assertTrue(Files.isDirectory(destination.getParent()));
        assertArrayEquals(content.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(destination));
    }

    @Test
    void createWriterUsesExplicitCharset() throws IOException {
        Path destination = tempDir.resolve("utf16.txt");
        String content = "A\u017d";

        try (Writer writer = IOUtil.createWriter(StandardCharsets.UTF_16LE, destination.toString())) {
            writer.write(content);
        }

        assertArrayEquals(content.getBytes(StandardCharsets.UTF_16LE), Files.readAllBytes(destination));
    }

    @Test
    void createWriterTruncatesExistingFile() throws IOException {
        Path destination = tempDir.resolve("existing.txt");
        Files.write(destination, "old content is much longer".getBytes(StandardCharsets.UTF_8));

        try (Writer writer = IOUtil.createWriter(StandardCharsets.UTF_8, destination.toString())) {
            writer.write("new");
        }

        assertArrayEquals("new".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(destination));
    }

    @Test
    void createWriterPreservesIoFailureAsCause() throws IOException {
        Path parentFile = tempDir.resolve("not-a-directory");
        byte[] originalContent = "keep this file".getBytes(StandardCharsets.UTF_8);
        Files.write(parentFile, originalContent);
        Path destination = parentFile.resolve("report.txt");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> IOUtil.createWriter(StandardCharsets.UTF_8, destination.toString()));

        assertInstanceOf(IOException.class, failure.getCause());
        assertArrayEquals(originalContent, Files.readAllBytes(parentFile));
    }
}
