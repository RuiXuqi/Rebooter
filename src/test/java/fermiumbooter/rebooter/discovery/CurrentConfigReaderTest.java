package fermiumbooter.rebooter.discovery;

import fermiumbooter.annotations.MixinConfig;
import net.minecraftforge.common.config.Config;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class CurrentConfigReaderTest {

    private static final String MIXIN_CONFIG = "Lfermiumbooter/annotations/MixinConfig;";
    private static final String MIXIN_TOGGLE = "Lfermiumbooter/annotations/MixinConfig$MixinToggle;";
    private static final String COMPAT_HANDLING = "Lfermiumbooter/annotations/MixinConfig$CompatHandling;";
    private static final String COMPAT_HANDLING_CONTAINER = "Lfermiumbooter/annotations/MixinConfig$CompatHandlingContainer;";
    private static final String COMPAT_HANDLINGS = "Lfermiumbooter/annotations/MixinConfig$CompatHandlings;";
    private static final String CONFIG_NAME = "Lnet/minecraftforge/common/config/Config$Name;";

    @Test
    void prefilterOnlySearchesTheConstantPool() throws Exception {
        byte[] classBytes = classBytesWithUtf8("unrelated", MIXIN_CONFIG);
        CountingInputStream input = new CountingInputStream(new ByteArrayInputStream(classBytes));

        boolean found = new ClassAnnotationScanner().scan(input).has(ClassAnnotationScanner.MIXIN_CONFIG);

        assertFalse(found);
        assertTrue(input.bytesRead() < classBytes.length, "method bodies must not be decompressed during prefiltering");
    }

    @Test
    void prefilterFindsTheAnnotationDescriptorInTheConstantPool() throws Exception {
        byte[] classBytes = classBytesWithUtf8(MIXIN_CONFIG, "unused tail");

        assertTrue(new ClassAnnotationScanner().scan(new ByteArrayInputStream(classBytes))
                .has(ClassAnnotationScanner.MIXIN_CONFIG));
    }

    @Test
    void prefilterReplaysTheConsumedPrefixToAsm() throws Exception {
        String resource = Fixture.class.getName().replace('.', '/') + ".class";
        ConfigReader.Result result;
        try (InputStream input = Objects.requireNonNull(
                Fixture.class.getClassLoader().getResourceAsStream(resource), resource)) {
            ClassAnnotationScanner.ScanResult scan = new ClassAnnotationScanner().scan(input);
            result = ConfigReader.scan(scan.classBytes());
        }

        assertNotNull(result);
        assertEquals("scanner-fixture", result.configName());
        assertEquals(1, result.toggles().size());
    }

    @Test
    void usesJvmFieldNameWhenConfigNameAnnotationIsMissing() throws Exception {
        ConfigReader.Result result = scan(FieldNameFixture.class);

        assertEquals("enabled", result.toggles().get(0).configFieldName());
    }

    @Test
    void usesJvmFieldNameWhenConfigNameAnnotationIsEmpty() throws Exception {
        ConfigReader.Result result = scan(EmptyConfigNameFixture.class);

        assertEquals("enabled", result.toggles().get(0).configFieldName());
    }

    @Test
    void readsCurrentCompatibilityContainer() throws Exception {
        assertCompatibilityContainer(COMPAT_HANDLING_CONTAINER);
    }

    @Test
    void readsHistoricalCompatibilityContainer() throws Exception {
        assertCompatibilityContainer(COMPAT_HANDLINGS);
    }

    private static void assertCompatibilityContainer(String descriptor) throws Exception {
        ConfigReader.Result result = ConfigReader.scan(new ByteArrayInputStream(compatibilityContainerFixture(descriptor)));

        java.util.List<CompatRule> rules = result.toggles().get(0).compatibilityRules();
        assertEquals(2, rules.size());
        assertEquals("forge", rules.get(0).modid());
        assertFalse(rules.get(0).desired());
        assertFalse(rules.get(0).disableMixin());
        assertFalse(rules.get(0).warnIngame());
        assertEquals("compatibility container", rules.get(0).reason());
        assertEquals("minecraft", rules.get(1).modid());
        assertTrue(rules.get(1).desired());
        assertEquals("", rules.get(1).reason());
    }

    private static ConfigReader.Result scan(Class<?> type) throws Exception {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream input = Objects.requireNonNull(
                type.getClassLoader().getResourceAsStream(resource), resource)) {
            return ConfigReader.scan(input);
        }
    }

    private static byte[] compatibilityContainerFixture(String descriptor) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "fixture/HistoricalConfig", null, "java/lang/Object", null);

        AnnotationVisitor config = writer.visitAnnotation(MIXIN_CONFIG, true);
        config.visit("name", "historical-fixture");
        config.visitEnd();

        FieldVisitor field = writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "enabled", "Z", null, null);
        AnnotationVisitor configName = field.visitAnnotation(CONFIG_NAME, true);
        configName.visit("value", "Enabled");
        configName.visitEnd();
        AnnotationVisitor toggle = field.visitAnnotation(MIXIN_TOGGLE, true);
        toggle.visit("earlyMixin", "mixins.historical.json");
        toggle.visit("defaultValue", true);
        toggle.visitEnd();

        AnnotationVisitor container = field.visitAnnotation(descriptor, true);
        AnnotationVisitor rules = container.visitArray("value");
        AnnotationVisitor incompatible = rules.visitAnnotation(null, COMPAT_HANDLING);
        incompatible.visit("modid", "forge");
        incompatible.visit("desired", false);
        incompatible.visit("disableMixin", false);
        incompatible.visit("warnIngame", false);
        incompatible.visit("reason", "compatibility container");
        incompatible.visitEnd();
        AnnotationVisitor compatible = rules.visitAnnotation(null, COMPAT_HANDLING);
        compatible.visit("modid", "minecraft");
        compatible.visit("desired", true);
        compatible.visitEnd();
        rules.visitEnd();
        container.visitEnd();
        field.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] classBytesWithUtf8(String constantPoolValue, String tail) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0xCAFEBABE);
        output.writeShort(0);
        output.writeShort(52);
        output.writeShort(2);
        output.writeByte(1);
        byte[] utf8 = constantPoolValue.getBytes(StandardCharsets.UTF_8);
        output.writeShort(utf8.length);
        output.write(utf8);
        output.write(new byte[16 * 1024]);
        output.write(tail.getBytes(StandardCharsets.UTF_8));
        output.close();
        return bytes.toByteArray();
    }

    private static final class CountingInputStream extends FilterInputStream {

        private int bytesRead;

        private CountingInputStream(InputStream input) {
            super(input);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                this.bytesRead++;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                this.bytesRead += read;
            }
            return read;
        }

        private int bytesRead() {
            return this.bytesRead;
        }
    }

    @MixinConfig(name = "scanner-fixture")
    public static class Fixture {

        @Config.Name("Enabled")
        @MixinConfig.MixinToggle(earlyMixin = "mixins.scanner.json", defaultValue = true)
        public static boolean enabled;
    }

    @MixinConfig(name = "field-name-fixture")
    public static class FieldNameFixture {

        @MixinConfig.MixinToggle(earlyMixin = "mixins.field-name.json", defaultValue = true)
        public static boolean enabled;
    }

    @MixinConfig(name = "empty-config-name-fixture")
    public static class EmptyConfigNameFixture {

        @Config.Name("")
        @MixinConfig.MixinToggle(earlyMixin = "mixins.empty-config-name.json", defaultValue = true)
        public static boolean enabled;
    }
}
