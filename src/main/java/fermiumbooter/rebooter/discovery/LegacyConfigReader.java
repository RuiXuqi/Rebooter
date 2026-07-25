package fermiumbooter.rebooter.discovery;

import fermiumbooter.rebooter.Reference;
import org.objectweb.asm.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

final class LegacyConfigReader extends ClassVisitor {
    private static final String CONFIG_NAME_DESCRIPTOR = "Lnet/minecraftforge/common/config/Config$Name;";
    private static final String CONFIG_DESCRIPTOR = "Lnet/minecraftforge/common/config/Config;";

    private final Map<String, String> configNames = new HashMap<>();
    private final Map<String, List<CompatRule>> compatibilityRules = new HashMap<>();
    private String configModId;
    private String configFileName = "";

    private LegacyConfigReader() {
        super(Opcodes.ASM5);
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
                    reader,
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return reader.result();
        } catch (IOException | IllegalArgumentException e) {
            Reference.LOGGER.error("Failed to read annotation metadata for {}", configClass.getName(), e);
            return Metadata.EMPTY;
        }
    }

    private Metadata result() {
        return new Metadata(
                this.configModId,
                this.configFileName,
                this.configNames,
                this.compatibilityRules);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        return CONFIG_DESCRIPTOR.equals(descriptor) ? new ConfigAnnotationVisitor(this) : null;
    }

    @Override
    public FieldVisitor visitField(
            int access,
            String name,
            String descriptor,
            String signature,
            Object value) {
        return new LegacyFieldVisitor(new FieldMetadata(name), this);
    }

    private void add(FieldMetadata field) {
        if (field.configName != null) {
            this.configNames.put(field.fieldName, field.configName);
        }
        if (!field.compatibilityRules.isEmpty()) {
            this.compatibilityRules.put(field.fieldName, field.compatibilityRules);
        }
    }

    static final class Metadata {
        private static final Metadata EMPTY = new Metadata(
                null,
                null,
                Collections.emptyMap(),
                Collections.emptyMap());
        private final String configModId;
        private final String configFileName;
        private final Map<String, String> configNames;
        private final Map<String, List<CompatRule>> compatibilityRules;

        private Metadata(
                String configModId,
                String configFileName,
                Map<String, String> configNames,
                Map<String, List<CompatRule>> compatibilityRules) {
            this.configModId = configModId;
            this.configFileName = configFileName;
            this.configNames = Collections.unmodifiableMap(new HashMap<>(configNames));
            this.compatibilityRules = immutableRules(compatibilityRules);
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
            List<CompatRule> rules = this.compatibilityRules.get(fieldName);
            return rules == null ? Collections.emptyList() : rules;
        }

        private static Map<String, List<CompatRule>> immutableRules(
                Map<String, List<CompatRule>> rulesByField) {
            Map<String, List<CompatRule>> copy = new HashMap<>();
            for (Map.Entry<String, List<CompatRule>> entry : rulesByField.entrySet()) {
                copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
            }
            return Collections.unmodifiableMap(copy);
        }
    }

    private static final class ConfigAnnotationVisitor extends AnnotationVisitor {
        private final LegacyConfigReader destination;

        private ConfigAnnotationVisitor(LegacyConfigReader destination) {
            super(Opcodes.ASM5);
            this.destination = destination;
        }

        @Override
        public void visit(String name, Object value) {
            if ("modid".equals(name) && value instanceof String) {
                this.destination.configModId = (String) value;
            } else if ("name".equals(name) && value instanceof String) {
                this.destination.configFileName = (String) value;
            }
        }
    }

    private static final class LegacyFieldVisitor extends FieldVisitor {
        private final FieldMetadata field;
        private final LegacyConfigReader destination;

        private LegacyFieldVisitor(FieldMetadata field, LegacyConfigReader destination) {
            super(Opcodes.ASM5);
            this.field = field;
            this.destination = destination;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (CONFIG_NAME_DESCRIPTOR.equals(descriptor)) {
                return new ConfigFieldNameVisitor(this.field);
            }
            return CompatAnnotationVisitor.create(descriptor, this.field.compatibilityRules);
        }

        @Override
        public void visitEnd() {
            this.destination.add(this.field);
        }
    }

    private static final class ConfigFieldNameVisitor extends AnnotationVisitor {
        private final FieldMetadata field;

        private ConfigFieldNameVisitor(FieldMetadata field) {
            super(Opcodes.ASM5);
            this.field = field;
        }

        @Override
        public void visit(String name, Object value) {
            if ("value".equals(name) && value instanceof String) {
                this.field.configName = (String) value;
            }
        }
    }

    private static final class FieldMetadata {
        private final String fieldName;
        private final List<CompatRule> compatibilityRules = new ArrayList<>();
        private String configName;

        private FieldMetadata(String fieldName) {
            this.fieldName = fieldName;
        }
    }
}
