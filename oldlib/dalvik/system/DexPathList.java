package oldlib.dalvik.system;

import dalvik.system.DexFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
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

        this.dexElements =
                makeInMemoryDexElements(buffers);
    }

    private Element[] makeInMemoryDexElements(
            ByteBuffer[] buffers) {

        ArrayList<Element> result =
                new ArrayList<Element>();

        for (ByteBuffer original : buffers) {

            if (original == null) {
                throw new NullPointerException(
                        "dex buffer == null"
                );
            }

            File dexFile = null;

            try {
                dexFile = createTemporaryDex(original);

                File optimized =
                        new File(
                                dexFile.getParentFile(),
                                "optimized"
                        );

                if (!optimized.exists()
                        && !optimized.mkdirs()
                        && !optimized.isDirectory()) {

                    throw new IOException(
                            "Cannot create optimized directory"
                    );
                }

                DexFile dex = DexFile.loadDex(
                        dexFile.getAbsolutePath(),
                        optimized.getAbsolutePath(),
                        0
                );

                result.add(
                        new Element(dex)
                );

                temporaryDexFiles.add(dexFile);

            } catch (IOException e) {

                if (dexFile != null) {
                    dexFile.delete();
                }

                throw new RuntimeException(
                        "Unable to load DEX from ByteBuffer",
                        e
                );
            }
        }

        return result.toArray(
                new Element[result.size()]
        );
    }

    private static File createTemporaryDex(
            ByteBuffer original)
            throws IOException {

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

        File output = new File(
                root,
                "memory-" +
                UUID.randomUUID().toString() +
                ".dex"
        );

        FileOutputStream out =
                new FileOutputStream(output);

        try {
            ByteBuffer buffer =
                    original.duplicate();

            byte[] temp = new byte[8192];

            while (buffer.hasRemaining()) {

                int count = Math.min(
                        buffer.remaining(),
                        temp.length
                );

                buffer.get(
                        temp,
                        0,
                        count
                );

                out.write(
                        temp,
                        0,
                        count
                );
            }

            out.flush();

        } finally {
            out.close();
        }

        return output;
    }

    private Element[] makeDexElements(
            String dexPath,
            File optimizedDirectory) {

        ArrayList<Element> result =
                new ArrayList<Element>();

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

                result.add(
                        new Element(dex)
                );

            } catch (IOException e) {
                throw new RuntimeException(
                        "Unable to load " + path,
                        e
                );
            }
        }

        return result.toArray(
                new Element[result.size()]
        );
    }

    Class<?> findClass(
            String name,
            List<Throwable> suppressed) {

        for (Element element : dexElements) {

            Class<?> clazz =
                    element.dexFile.loadClassBinaryName(
                            name,
                            definingContext,
                            suppressed
                    );

            if (clazz != null) {
                return clazz;
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
        return java.util.Collections
                .<java.net.URL>emptyList()
                .iterator();
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
