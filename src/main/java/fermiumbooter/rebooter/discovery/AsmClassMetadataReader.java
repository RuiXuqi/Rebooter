package fermiumbooter.rebooter.discovery;

import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

final class AsmClassMetadataReader {
    private static final int READER_VERSION = 1;

    private AsmClassMetadataReader() {
    }

    static String cacheProfile() {
        return "class-metadata-reader-v" + READER_VERSION + '\n'
                + CurrentConfigReader.cacheProfile()
                + CompatAnnotationVisitor.cacheProfile();
    }

    static ClassScanResult read(byte[] classBytes, int annotationKinds) {
        CurrentConfigReader configReader = has(
                annotationKinds,
                ClassDescriptorPrefilter.MIXIN_CONFIG)
                ? new CurrentConfigReader()
                : null;
        ModMetadataVisitor metadataVisitor = new ModMetadataVisitor(configReader, annotationKinds);
        new ClassReader(classBytes).accept(
                metadataVisitor,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ClassScanResult(
                configReader == null ? null : configReader.result(),
                metadataVisitor.modId);
    }

    private static boolean has(int annotationKinds, int expected) {
        return (annotationKinds & expected) != 0;
    }

    private static final class ModMetadataVisitor extends ClassVisitor {
        private final boolean inspectForgeMod;
        private String modId;

        private ModMetadataVisitor(ClassVisitor delegate, int annotationKinds) {
            super(Opcodes.ASM5, delegate);
            this.inspectForgeMod = has(annotationKinds, ClassDescriptorPrefilter.FORGE_MOD);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (this.inspectForgeMod
                    && ClassDescriptorPrefilter.FORGE_MOD_DESCRIPTOR.equals(descriptor)) {
                return new ForgeModAnnotationVisitor(this);
            }
            return super.visitAnnotation(descriptor, visible);
        }
    }

    private static final class ForgeModAnnotationVisitor extends AnnotationVisitor {
        private final ModMetadataVisitor destination;

        private ForgeModAnnotationVisitor(ModMetadataVisitor destination) {
            super(Opcodes.ASM5);
            this.destination = destination;
        }

        @Override
        public void visit(String name, Object value) {
            if ("modid".equals(name)
                    && value instanceof String
                    && StringUtils.isNotBlank((String) value)) {
                this.destination.modId = (String) value;
            }
        }
    }

}
