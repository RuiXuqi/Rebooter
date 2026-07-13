package fermiumbooter.rebooter.discovery;

import org.apache.commons.lang3.StringUtils;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import javax.annotation.Nullable;

final class ModAnnotationReader {
    private String modId;

    private ModAnnotationReader() {
    }

    @Nullable
    static String scan(byte[] classBytes) {
        ModAnnotationReader reader = new ModAnnotationReader();
        new ClassReader(classBytes).accept(
                reader.classVisitor(),
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return reader.modId;
    }

    private ClassVisitor classVisitor() {
        return new ClassVisitor(Opcodes.ASM5) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!ClassAnnotationScanner.FORGE_MOD_DESCRIPTOR.equals(descriptor)) return null;
                return new AnnotationVisitor(Opcodes.ASM5) {
                    @Override
                    public void visit(String name, Object value) {
                        if ("modid".equals(name) && value instanceof String && StringUtils.isNotBlank((String) value)) {
                            ModAnnotationReader.this.modId = (String) value;
                        }
                    }
                };
            }
        };
    }
}
