package dev.omatheusmesmo.qlawkus.tools.brag;

import dev.omatheusmesmo.qlawkus.config.NamedDatasourceConfigSource;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Turns the Brag database on when its URL is configured at boot and leaves it off otherwise, so the
 * extension can sit in an image that runs without Postgres. See {@link NamedDatasourceConfigSource}.
 */
public class BragDatasourceConfigSourceFactory implements ConfigSourceFactory {

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        return NamedDatasourceConfigSource.sources(context, "brag");
    }
}
