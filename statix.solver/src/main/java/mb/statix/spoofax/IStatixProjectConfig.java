package mb.statix.spoofax;

import java.util.Set;

public interface IStatixProjectConfig {

    Set<String> parallelLanguages(Set<String> defaultValue);

    Integer messageTraceLength(Integer defaultValue);

    Integer messageTermDepth(Integer defaultValue);

    public static int DEFAULT_MESSAGE_TRACE_LENGTH = 0;
    public static int DEFAULT_MESSAGE_TERM_DEPTH = 3;

    public static IStatixProjectConfig NULL = new StatixProjectConfig(null, null, null);

}