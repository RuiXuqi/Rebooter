package fermiumbooter.rebooter.discovery;

import com.google.common.annotations.VisibleForTesting;
import org.objectweb.asm.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ConfigReader {
    private static final int RESULT_SCHEMA_VERSION = 1;
    static final String MIXIN_CONFIG = "Lfermiumbooter/annotations/MixinConfig;";
    private static final String CONFIG_NAME = "Lnet/minecraftforge/common/config/Config$Name;";
    private static final String MIXIN_TOGGLE = "Lfermiumbooter/annotations/MixinConfig$MixinToggle;";
    private final CompatAnnotationReader compatibilityAnnotations = new CompatAnnotationReader();
    private String configName;
    private List<Toggle> toggles;

    ConfigReader() {
    }

    static String cacheProfile() {
        return "config-reader-result-v" + RESULT_SCHEMA_VERSION + '\n'
                + MIXIN_CONFIG + '\n'
                + CONFIG_NAME + '\n'
                + MIXIN_TOGGLE + '\n'
                + "toggle-defaults:early=;late=;enabled=false\n";
    }

    @Nullable
    Result result() {
        return this.configName != null && !this.configName.isEmpty() && this.toggles != null
                ? new Result(this.configName, this.toggles) : null;
    }

    ClassVisitor configClassVisitor() {
        return new ClassVisitor(Opcodes.ASM5) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!MIXIN_CONFIG.equals(descriptor)) {
                    return null;
                }
                ConfigReader.this.configName = "";
                return ConfigReader.this.mixinConfigVisitor();
            }

            @Override
            public FieldVisitor visitField(
                    int access,
                    String name,
                    String descriptor,
                    String signature,
                    Object value) {
                if (ConfigReader.this.configName == null || !"Z".equals(descriptor)) {
                    return null;
                }
                return ConfigReader.this.toggleFieldVisitor(
                        name, value instanceof Boolean && (Boolean) value);
            }
        };
    }

    private AnnotationVisitor mixinConfigVisitor() {
        return new AnnotationVisitor(Opcodes.ASM5) {
            @Override
            public void visit(String name, Object value) {
                if ("name".equals(name) && value instanceof String) {
                    ConfigReader.this.configName = (String) value;
                }
            }
        };
    }

    private FieldVisitor toggleFieldVisitor(String fieldName, boolean fieldDefaultValue) {
        ToggleState toggleState = new ToggleState(fieldName, fieldDefaultValue);
        return new FieldVisitor(Opcodes.ASM5) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (CONFIG_NAME.equals(descriptor)) {
                    return toggleState.configNameVisitor();
                }
                if (MIXIN_TOGGLE.equals(descriptor)) {
                    toggleState.initialize();
                    return toggleState.mixinToggleVisitor();
                }
                return ConfigReader.this.compatibilityAnnotations.visit(toggleState.fieldName, descriptor);
            }

            @Override
            public void visitEnd() {
                if (!toggleState.hasMixinToggle) {
                    return;
                }
                if (ConfigReader.this.toggles == null) {
                    ConfigReader.this.toggles = new ArrayList<>();
                }
                ConfigReader.this.toggles.add(new Toggle(
                        toggleState.configFieldName,
                        toggleState.earlyMixinName,
                        toggleState.lateMixinName,
                        toggleState.defaultValue,
                        ConfigReader.this.compatibilityAnnotations.rulesFor(toggleState.fieldName)));
            }
        };
    }

    private static final class ToggleState {
        private final String fieldName;
        private String configFieldName;
        private String earlyMixinName;
        private String lateMixinName;
        private boolean defaultValue;
        private boolean hasMixinToggle;

        private ToggleState(String fieldName, boolean defaultValue) {
            this.fieldName = fieldName;
            this.configFieldName = fieldName;
            this.earlyMixinName = null;
            this.lateMixinName = null;
            this.defaultValue = defaultValue;
            this.hasMixinToggle = false;
        }

        private AnnotationVisitor configNameVisitor() {
            return new AnnotationVisitor(Opcodes.ASM5) {
                @Override
                public void visit(String name, Object value) {
                    if ("value".equals(name) && value instanceof String && !((String) value).isEmpty()) {
                        ToggleState.this.configFieldName = (String) value;
                    }
                }
            };
        }

        private AnnotationVisitor mixinToggleVisitor() {
            return new AnnotationVisitor(Opcodes.ASM5) {
                @Override
                public void visit(String name, Object value) {
                    if ("earlyMixin".equals(name) && value instanceof String) {
                        ToggleState.this.earlyMixinName = (String) value;
                    } else if ("lateMixin".equals(name) && value instanceof String) {
                        ToggleState.this.lateMixinName = (String) value;
                    } else if ("defaultValue".equals(name) && value instanceof Boolean) {
                        ToggleState.this.defaultValue = (Boolean) value;
                    }
                }
            };
        }

        private void initialize() {
            this.hasMixinToggle = true;
            this.earlyMixinName = "";
            this.lateMixinName = "";
            this.defaultValue = false;
        }
    }

    static final class Result {
        private final String configName;
        private final List<Toggle> toggles;

        Result(String configName, List<Toggle> toggles) {
            this.configName = configName;
            this.toggles = Collections.unmodifiableList(new ArrayList<>(toggles));
        }

        String configName() {
            return this.configName;
        }

        List<Toggle> toggles() {
            return this.toggles;
        }
    }

    static final class Toggle {
        private final String configFieldName;
        private final String earlyMixinName;
        private final String lateMixinName;
        private final boolean defaultValue;
        private final List<CompatRule> compatibilityRules;

        Toggle(
                String configFieldName,
                String earlyMixinName,
                String lateMixinName,
                boolean defaultValue,
                List<CompatRule> compatibilityRules) {
            this.configFieldName = configFieldName;
            this.earlyMixinName = earlyMixinName;
            this.lateMixinName = lateMixinName;
            this.defaultValue = defaultValue;
            this.compatibilityRules = Collections.unmodifiableList(new ArrayList<>(compatibilityRules));
        }

        String configFieldName() {
            return this.configFieldName;
        }

        String earlyMixinName() {
            return this.earlyMixinName;
        }

        String lateMixinName() {
            return this.lateMixinName;
        }

        boolean defaultValue() {
            return this.defaultValue;
        }

        List<CompatRule> compatibilityRules() {
            return this.compatibilityRules;
        }
    }

    @Nullable
    @VisibleForTesting
    static Result scan(InputStream input) throws IOException {
        return scan(new ClassReader(input));
    }

    @Nullable
    @VisibleForTesting
    static Result scan(byte[] classBytes) {
        return scan(new ClassReader(classBytes));
    }

    @Nullable
    private static Result scan(ClassReader classReader) {
        ConfigReader reader = new ConfigReader();
        classReader.accept(
                reader.configClassVisitor(),
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return reader.result();
    }
}
