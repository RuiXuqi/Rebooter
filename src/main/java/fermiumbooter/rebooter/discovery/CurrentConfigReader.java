package fermiumbooter.rebooter.discovery;

import com.google.common.annotations.VisibleForTesting;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

final class CurrentConfigReader extends ClassVisitor {
    private static final int RESULT_SCHEMA_VERSION = 1;
    static final String MIXIN_CONFIG_DESCRIPTOR = "Lfermiumbooter/annotations/MixinConfig;";
    private static final String CONFIG_NAME_DESCRIPTOR = "Lnet/minecraftforge/common/config/Config$Name;";
    private static final String MIXIN_TOGGLE_DESCRIPTOR = "Lfermiumbooter/annotations/MixinConfig$MixinToggle;";

    private final List<DiscoveredConfig.Toggle> toggles = new ArrayList<>();
    private String configName;

    CurrentConfigReader() {
        super(Opcodes.ASM5);
    }

    static String cacheProfile() {
        return "config-reader-result-v" + RESULT_SCHEMA_VERSION + '\n'
                + MIXIN_CONFIG_DESCRIPTOR + '\n'
                + CONFIG_NAME_DESCRIPTOR + '\n'
                + MIXIN_TOGGLE_DESCRIPTOR + '\n'
                + "toggle-defaults:early=;late=;enabled=false\n";
    }

    @Nullable
    DiscoveredConfig result() {
        return this.configName != null && !this.configName.isEmpty() && !this.toggles.isEmpty()
                ? new DiscoveredConfig(this.configName, this.toggles)
                : null;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
        if (!MIXIN_CONFIG_DESCRIPTOR.equals(descriptor)) {
            return null;
        }
        this.configName = "";
        return new ConfigAnnotationVisitor(this);
    }

    @Override
    public FieldVisitor visitField(
            int access,
            String name,
            String descriptor,
            String signature,
            Object value) {
        if (this.configName == null || !"Z".equals(descriptor)) {
            return null;
        }
        return new ToggleFieldVisitor(
                new ToggleBuilder(name, value instanceof Boolean && (Boolean) value),
                this.toggles);
    }

    @Nullable
    @VisibleForTesting
    static DiscoveredConfig scan(InputStream input) throws IOException {
        return scan(new ClassReader(input));
    }

    @Nullable
    private static DiscoveredConfig scan(ClassReader classReader) {
        CurrentConfigReader reader = new CurrentConfigReader();
        classReader.accept(
                reader,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return reader.result();
    }

    private static final class ConfigAnnotationVisitor extends AnnotationVisitor {
        private final CurrentConfigReader destination;

        private ConfigAnnotationVisitor(CurrentConfigReader destination) {
            super(Opcodes.ASM5);
            this.destination = destination;
        }

        @Override
        public void visit(String name, Object value) {
            if ("name".equals(name) && value instanceof String) {
                this.destination.configName = (String) value;
            }
        }
    }

    private static final class ToggleFieldVisitor extends FieldVisitor {
        private final ToggleBuilder toggle;
        private final List<DiscoveredConfig.Toggle> destination;

        private ToggleFieldVisitor(
                ToggleBuilder toggle,
                List<DiscoveredConfig.Toggle> destination) {
            super(Opcodes.ASM5);
            this.toggle = toggle;
            this.destination = destination;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (CONFIG_NAME_DESCRIPTOR.equals(descriptor)) {
                return new ConfigFieldNameVisitor(this.toggle);
            }
            if (MIXIN_TOGGLE_DESCRIPTOR.equals(descriptor)) {
                this.toggle.initializeMixinToggle();
                return new MixinToggleVisitor(this.toggle);
            }
            return CompatAnnotationVisitor.create(descriptor, this.toggle.compatibilityRules);
        }

        @Override
        public void visitEnd() {
            if (this.toggle.hasMixinToggle) {
                this.destination.add(this.toggle.build());
            }
        }
    }

    private static final class ConfigFieldNameVisitor extends AnnotationVisitor {
        private final ToggleBuilder toggle;

        private ConfigFieldNameVisitor(ToggleBuilder toggle) {
            super(Opcodes.ASM5);
            this.toggle = toggle;
        }

        @Override
        public void visit(String name, Object value) {
            if ("value".equals(name) && value instanceof String && !((String) value).isEmpty()) {
                this.toggle.configFieldName = (String) value;
            }
        }
    }

    private static final class MixinToggleVisitor extends AnnotationVisitor {
        private final ToggleBuilder toggle;

        private MixinToggleVisitor(ToggleBuilder toggle) {
            super(Opcodes.ASM5);
            this.toggle = toggle;
        }

        @Override
        public void visit(String name, Object value) {
            if ("earlyMixin".equals(name) && value instanceof String) {
                this.toggle.earlyMixinName = (String) value;
            } else if ("lateMixin".equals(name) && value instanceof String) {
                this.toggle.lateMixinName = (String) value;
            } else if ("defaultValue".equals(name) && value instanceof Boolean) {
                this.toggle.defaultValue = (Boolean) value;
            }
        }
    }

    private static final class ToggleBuilder {
        private final List<CompatRule> compatibilityRules = new ArrayList<>();
        private String configFieldName;
        private String earlyMixinName;
        private String lateMixinName;
        private boolean defaultValue;
        private boolean hasMixinToggle;

        private ToggleBuilder(String fieldName, boolean fieldDefaultValue) {
            this.configFieldName = fieldName;
            this.defaultValue = fieldDefaultValue;
        }

        private void initializeMixinToggle() {
            this.hasMixinToggle = true;
            this.earlyMixinName = "";
            this.lateMixinName = "";
            this.defaultValue = false;
        }

        private DiscoveredConfig.Toggle build() {
            return new DiscoveredConfig.Toggle(
                    this.configFieldName,
                    this.earlyMixinName,
                    this.lateMixinName,
                    this.defaultValue,
                    this.compatibilityRules);
        }
    }
}
