package fermiumbooter.rebooter.discovery;

import fermiumbooter.rebooter.Reference;
import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class LegacyConfigReader {
    private static final String CONFIG_NAME = "Lnet/minecraftforge/common/config/Config$Name;";
    private static final String CONFIG = "Lnet/minecraftforge/common/config/Config;";
    private final CompatAnnotationReader compatibilityAnnotations = new CompatAnnotationReader();
    private final Map<String, String> configNames = new HashMap<>();
    private String configModId;
    private String configFileName = "";

    private LegacyConfigReader() {
    }

    static Metadata scan(Class<?> configClass) {
        String resourceName = "/" + configClass.getName().replace('.', '/') + ".class";
        try (InputStream input = configClass.getResourceAsStream(resourceName)) {
            if (input == null) {
                Reference.LOGGER.error("Cannot read annotation metadata for {}", configClass.getName());
                return Metadata.EMPTY;
            }
            LegacyConfigReader reader = new LegacyConfigReader();
            new ClassReader(input).accept(
                    reader.metadataVisitor(),
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return new Metadata(
                    reader.configModId,
                    reader.configFileName,
                    reader.configNames,
                    reader.compatibilityAnnotations);
        } catch (IOException | IllegalArgumentException e) {
            Reference.LOGGER.error("Failed to read annotation metadata for {}", configClass.getName(), e);
            return Metadata.EMPTY;
        }
    }

    static final class Metadata {
        private static final Metadata EMPTY = new Metadata(
                null, null,
                Collections.emptyMap(), new CompatAnnotationReader());
        private final String configModId;
        private final String configFileName;
        private final Map<String, String> configNames;
        private final CompatAnnotationReader compatibilityAnnotations;

        private Metadata(
                String configModId,
                String configFileName,
                Map<String, String> configNames,
                CompatAnnotationReader compatibilityAnnotations
        ) {
            this.configModId = configModId;
            this.configFileName = configFileName;
            this.configNames = configNames;
            this.compatibilityAnnotations = compatibilityAnnotations;
        }

        boolean isForgeConfig() {
            return this.configModId != null;
        }

        String configFileName() {
            return this.configFileName == null || this.configFileName.isEmpty()
                    ? this.configModId
                    : this.configFileName;
        }

        String configName(String fieldName) {
            String annotationName = this.configNames.get(fieldName);
            return annotationName == null || annotationName.isEmpty() ? fieldName : annotationName;
        }

        List<CompatRule> compatibilityRules(String fieldName) {
            return this.compatibilityAnnotations.rulesFor(fieldName);
        }
    }

    private ClassVisitor metadataVisitor() {
        return new ClassVisitor(Opcodes.ASM5) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                return CONFIG.equals(descriptor) ? LegacyConfigReader.this.configVisitor() : null;
            }

            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value) {
                return LegacyConfigReader.this.fieldVisitor(name);
            }
        };
    }

    private AnnotationVisitor configVisitor() {
        return new AnnotationVisitor(Opcodes.ASM5) {
            @Override
            public void visit(String name, Object value) {
                if ("modid".equals(name) && value instanceof String) {
                    LegacyConfigReader.this.configModId = (String) value;
                } else if ("name".equals(name) && value instanceof String) {
                    LegacyConfigReader.this.configFileName = (String) value;
                }
            }
        };
    }

    private FieldVisitor fieldVisitor(String fieldName) {
        return new FieldVisitor(Opcodes.ASM5) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (CONFIG_NAME.equals(descriptor)) {
                    return LegacyConfigReader.this.configNameVisitor(fieldName);
                }
                return LegacyConfigReader.this.compatibilityAnnotations.visit(fieldName, descriptor);
            }
        };
    }

    private AnnotationVisitor configNameVisitor(String fieldName) {
        return new AnnotationVisitor(Opcodes.ASM5) {
            @Override
            public void visit(String name, Object value) {
                if ("value".equals(name) && value instanceof String) {
                    LegacyConfigReader.this.configNames.put(fieldName, (String) value);
                }
            }
        };
    }
}
