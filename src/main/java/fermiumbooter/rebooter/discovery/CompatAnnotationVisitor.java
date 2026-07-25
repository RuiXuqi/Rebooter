package fermiumbooter.rebooter.discovery;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;

final class CompatAnnotationVisitor {
    private static final int RESULT_SCHEMA_VERSION = 1;
    private static final String COMPAT_HANDLING = "Lfermiumbooter/annotations/MixinConfig$CompatHandling;";
    private static final String COMPAT_HANDLING_CONTAINER = "Lfermiumbooter/annotations/MixinConfig$CompatHandlingContainer;";
    private static final String COMPAT_HANDLINGS = "Lfermiumbooter/annotations/MixinConfig$CompatHandlings;";

    private CompatAnnotationVisitor() {
    }

    static String cacheProfile() {
        return "compat-reader-result-v" + RESULT_SCHEMA_VERSION + '\n'
                + COMPAT_HANDLING + '\n'
                + COMPAT_HANDLING_CONTAINER + '\n'
                + COMPAT_HANDLINGS + '\n'
                + "defaults:modid=;desired=true;disable=true;warn=true;reason=\n";
    }

    static AnnotationVisitor create(String descriptor, List<CompatRule> destination) {
        if (COMPAT_HANDLING.equals(descriptor)) {
            return new RuleVisitor(destination);
        }
        if (COMPAT_HANDLING_CONTAINER.equals(descriptor) || COMPAT_HANDLINGS.equals(descriptor)) {
            return new ContainerVisitor(destination);
        }
        return null;
    }

    private static final class ContainerVisitor extends AnnotationVisitor {
        private final List<CompatRule> destination;

        private ContainerVisitor(List<CompatRule> destination) {
            super(Opcodes.ASM5);
            this.destination = destination;
        }

        @Override
        public AnnotationVisitor visitArray(String name) {
            return "value".equals(name) ? new RuleArrayVisitor(this.destination) : null;
        }
    }

    private static final class RuleArrayVisitor extends AnnotationVisitor {
        private final List<CompatRule> destination;

        private RuleArrayVisitor(List<CompatRule> destination) {
            super(Opcodes.ASM5);
            this.destination = destination;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String ignored, String descriptor) {
            return COMPAT_HANDLING.equals(descriptor) ? new RuleVisitor(this.destination) : null;
        }
    }

    private static final class RuleVisitor extends AnnotationVisitor {
        private final List<CompatRule> destination;
        private String modid = "";
        private boolean desired = true;
        private boolean disableMixin = true;
        private boolean warnIngame = true;
        private String reason = "";

        private RuleVisitor(List<CompatRule> destination) {
            super(Opcodes.ASM5);
            this.destination = destination;
        }

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
            this.destination.add(new CompatRule(
                    this.modid,
                    this.desired,
                    this.disableMixin,
                    this.warnIngame,
                    this.reason));
        }
    }
}
