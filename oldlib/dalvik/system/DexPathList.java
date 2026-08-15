package oldlib.dalvik.system;

import dalvik.system.DexFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DexPathList {

    private final ClassLoader definingContext;

    private Element[] dexElements;

    private final List<File> temporaryDexFiles =
            new ArrayList<File>();

    private final List<File> nativeLibraryDirectories =
            new ArrayList<File>();

    DexPathList(
            ClassLoader definingContext,
            String dexPath,
            String libraryPath,
            File optimizedDirectory) {

        this.definingContext = definingContext;

        if (dexPath == null) {
            dexPath = "";
        }

        this.dexElements = makeDexElements(
                dexPath,
                optimizedDirectory
        );
    }

    DexPathList(
            ClassLoader definingContext,
            ByteBuffer[] buffers) {

        this.definingContext = definingContext;
        List<IOException> suppressedExceptions = new ArrayList<IOException>();
        this.dexElements = makeInMemoryDexElements(buffers, suppressedExceptions);
    }

    private static Element[] makeInMemoryDexElements(
        ByteBuffer[] buffers,
        List<IOException> suppressedExceptions) {

    Element[] elements =
            new Element[buffers.length];

    int elementPos = 0;

    for (ByteBuffer buf : buffers) {
        try {
            File dexFile =
                    createTemporaryDexFile(buf);

            File optimizedDirectory =
                    dexFile.getParentFile();

            DexFile dex = DexFile.loadDex(
                    dexFile.getAbsolutePath(),
                    optimizedDirectory.getAbsolutePath(),
                    0
            );

            elements[elementPos++] =
                    new Element(dex);

        } catch (IOException e) {
            if (suppressedExceptions != null) {
                suppressedExceptions.add(e);
            }
        }
    }

    if (elementPos != elements.length) {
        Element[] trimmed =
                new Element[elementPos];

        System.arraycopy(
                elements,
                0,
                trimmed,
                0,
                elementPos
        );

        elements = trimmed;
    }

    return elements;
}

private static File createTemporaryDexFile(
        ByteBuffer source) throws IOException {

    File root = new File(
            System.getProperty("java.io.tmpdir"),
            "oldlib-dex"
    );

    if (!root.exists()
            && !root.mkdirs()
            && !root.isDirectory()) {
        throw new IOException(
                "Cannot create " + root
        );
    }

    File dexFile = File.createTempFile(
            "memory-",
            ".dex",
            root
    );

    FileOutputStream output =
            new FileOutputStream(dexFile);

    try {
        ByteBuffer buffer =
                source.duplicate();

        byte[] temp = new byte[8192];

        while (buffer.hasRemaining()) {
            int count = Math.min(
                    buffer.remaining(),
                    temp.length
            );

            buffer.get(temp, 0, count);
            output.write(temp, 0, count);
        }

        output.flush();

    } finally {
        output.close();
    }

    return dexFile;
    }

    private Element[] makeDexElements(
            String dexPath,
            File optimizedDirectory) {
        ArrayList<Element> result = new ArrayList<Element>();
        if (dexPath.length() == 0) {
            return new Element[0];
        }
        String[] paths =
                dexPath.split(
                        java.util.regex.Pattern.quote(
                                File.pathSeparator
                        )
                );

        for (String path : paths) {
            if (path.length() == 0) {
                continue;
           }
            try {
                DexFile dex = DexFile.loadDex(
                        path,
                        optimizedDirectory == null
                                ? null
                                : optimizedDirectory.getAbsolutePath(),
                        0
                );

                result.add(new Element(dex));

            } catch (IOException e) {
                throw new RuntimeException(
                        "Unable to load " + path,
                        e
                );
            }
        }

        return result.toArray(new Element[result.size()]);
    }

    Class<?> findClass(
            String name,
            List<Throwable> suppressed) {
        for (Element element : dexElements) {

            try {
                Class<?> clazz = element.dexFile.loadClass(name, definingContext);

            if (clazz != null) {
               return clazz;
                }
            } catch (Throwable e) {
                 if (suppressed != null) {
                    suppressed.add(e);
                 }
            }
        }

        return null;
    }

    public void addDexPath(
            String path,
            List<IOException> suppressed) {
        Element[] newElements =
                makeDexElements(
                        path,
                        null
                );

        Element[] old =
                dexElements;
        Element[] merged =
                new Element[
                        old.length +
                        newElements.length
                ];

        System.arraycopy(
                newElements,
                0,
                merged,
                0,
                newElements.length
        );

        System.arraycopy(
                old,
                0,
                merged,
                newElements.length,
                old.length
        );

        dexElements = merged;
    }

    public String findLibrary(String name) {
        return null;
    }

    public java.net.URL findResource(String name) {
        return null;
    }

    public java.util.Enumeration<java.net.URL>
    findResources(String name)
            throws IOException {
        return Collections.enumeration(Collections.<URL>emptyList());
    }

    public List<File>
    getNativeLibraryDirectories() {
        return nativeLibraryDirectories;
    }

    public String toString() {
        return "DexPathList" +
                dexElements.length +
                " elements";
    }

    public static final class Element {

        final DexFile dexFile;

        Element(DexFile dexFile) {
            this.dexFile = dexFile;
        }

        public String toString() {
            return String.valueOf(dexFile);
        }
    }
}
