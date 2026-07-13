package fermiumbooter.rebooter.discovery;

import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import javax.annotation.Nullable;

final class ClassMetadataReader {
    private static final int READER_VERSION = 1;
    private String modId;

    private ClassMetadataReader() {
    }

    static String cacheProfile() {
        return "class-metadata-reader-v" + READER_VERSION + '\n'
                + ConfigReader.cacheProfile()
                + CompatAnnotationReader.cacheProfile();
    }

    static Metadata scan(byte[] classBytes, int flags) {
        ConfigReader configReader = (flags & ClassAnnotationScanner.MIXIN_CONFIG) != 0
                ? new ConfigReader() : null;
        ClassMetadataReader metadataReader = new ClassMetadataReader();
        ClassVisitor configVisitor = configReader == null ? null : configReader.configClassVisitor();
        new ClassReader(classBytes).accept(
                metadataReader.classVisitor(configVisitor, flags),
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new Metadata(
                configReader == null ? null : configReader.result(),
                metadataReader.modId);
    }

    private ClassVisitor classVisitor(@Nullable ClassVisitor configVisitor, int flags) {
        return new ClassVisitor(Opcodes.ASM5, configVisitor) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if ((flags & ClassAnnotationScanner.FORGE_MOD) != 0
                        && ClassAnnotationScanner.FORGE_MOD_DESCRIPTOR.equals(descriptor)) {
                    return ClassMetadataReader.this.modAnnotationVisitor();
                }
                return super.visitAnnotation(descriptor, visible);
            }
        };
    }

    private AnnotationVisitor modAnnotationVisitor() {
        return new AnnotationVisitor(Opcodes.ASM5) {
            @Override
            public void visit(String name, Object value) {
                if ("modid".equals(name)
                        && value instanceof String
                        && StringUtils.isNotBlank((String) value)) {
                    ClassMetadataReader.this.modId = (String) value;
                }
            }
        };
    }

    static final class Metadata {
        @Nullable
        private final ConfigReader.Result configResult;
        @Nullable
        private final String modId;

        private Metadata(@Nullable ConfigReader.Result configResult, @Nullable String modId) {
            this.configResult = configResult;
            this.modId = modId;
        }

        @Nullable
        ConfigReader.Result configResult() {
            return this.configResult;
        }

        @Nullable
        String modId() {
            return this.modId;
        }
    }
}
