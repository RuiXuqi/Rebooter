package {{ package }};

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Reference {
    private Reference() {
    }

    public static final String MOD_ID = "{{ mod_id }}";
    public static final String MOD_NAME = "{{ mod_name }}";
    public static final String VERSION = "{{ mod_version }}";
    public static final Logger LOGGER = LogManager.getLogger(MOD_NAME);
}
