package fermiumbooter.rebooter.discovery;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

import java.util.*;

final class CompatAnnotationReader {
    private static final String COMPAT_HANDLING = "Lfermiumbooter/annotations/MixinConfig$CompatHandling;";
    private static final String COMPAT_HANDLING_CONTAINER = "Lfermiumbooter/annotations/MixinConfig$CompatHandlingContainer;";
    private static final String COMPAT_HANDLINGS = "Lfermiumbooter/annotations/MixinConfig$CompatHandlings;";
    private final Map<String, List<CompatRule>> rulesByField = new HashMap<>();

    AnnotationVisitor visit(String fieldName, String descriptor) {
        if (COMPAT_HANDLING.equals(descriptor)) {
            return this.ruleVisitor(fieldName);
        }
        if (COMPAT_HANDLING_CONTAINER.equals(descriptor) || COMPAT_HANDLINGS.equals(descriptor)) {
            return this.containerVisitor(fieldName);
        }
        return null;
    }

    List<CompatRule> rulesFor(String fieldName) {
        List<CompatRule> rules = this.rulesByField.get(fieldName);
        return rules == null || rules.isEmpty() ? Collections.emptyList() : new ArrayList<>(rules);
    }

    private AnnotationVisitor containerVisitor(String fieldName) {
        return new AnnotationVisitor(Opcodes.ASM5) {
            @Override
            public AnnotationVisitor visitArray(String name) {
                return "value".equals(name) ? new AnnotationVisitor(Opcodes.ASM5) {
                    @Override
                    public AnnotationVisitor visitAnnotation(String ignored, String descriptor) {
                        return COMPAT_HANDLING.equals(descriptor) ? CompatAnnotationReader.this.ruleVisitor(fieldName) : null;
                    }
                } : null;
            }
        };
    }

    private AnnotationVisitor ruleVisitor(String fieldName) {
        return new AnnotationVisitor(Opcodes.ASM5) {
            private String modid = "";
            private boolean desired = true;
            private boolean disableMixin = true;
            private boolean warnIngame = true;
            private String reason = "";

            @Override
            public void visit(String name, Object value) {
                if ("modid".equals(name) && value instanceof String) {
                    this.modid = (String) value;
                } else if ("desired".equals(name) && value instanceof Boolean) {
                    this.desired = (Boolean) value;
                } else if ("disableMixin".equals(name) && value instanceof Boolean) {
                    this.disableMixin = (Boolean) value;
                } else if ("warnIngame".equals(name) && value instanceof Boolean) {
                    this.warnIngame = (Boolean) value;
                } else if ("reason".equals(name) && value instanceof String) {
                    this.reason = (String) value;
                }
            }

            @Override
            public void visitEnd() {
                CompatAnnotationReader.this.rulesByField.computeIfAbsent(fieldName, ignored -> new ArrayList<>())
                        .add(new CompatRule(this.modid, this.desired,
                                this.disableMixin, this.warnIngame, this.reason));
            }
        };
    }
}
