package com.postonmywall.publish;

import com.postonmywall.common.Platform;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registry that maps each Platform enum value to its SocialMediaAdapter bean.
 * Spring injects all adapter implementations automatically via the List constructor.
 */
@Component
public class SocialAdapterRegistry {

    private final Map<Platform, SocialMediaAdapter> registry;

    public SocialAdapterRegistry(List<SocialMediaAdapter> adapters) {
        registry = new EnumMap<>(Platform.class);
        adapters.forEach(adapter -> registry.put(adapter.getPlatform(), adapter));
    }

    public SocialMediaAdapter get(Platform platform) {
        SocialMediaAdapter adapter = registry.get(platform);
        if (adapter == null)
            throw new IllegalStateException("No adapter registered for platform: " + platform);
        return adapter;
    }
}
