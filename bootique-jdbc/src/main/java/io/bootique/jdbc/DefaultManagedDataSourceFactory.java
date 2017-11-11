package io.bootique.jdbc;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.google.inject.Injector;
import io.bootique.annotation.BQConfig;
import io.bootique.jackson.JacksonService;
import io.bootique.meta.config.ConfigMapMetadata;
import io.bootique.meta.config.ConfigMetadataNode;
import io.bootique.meta.config.ConfigMetadataVisitor;
import io.bootique.meta.config.ConfigObjectMetadata;
import io.bootique.meta.module.ModuleMetadata;
import io.bootique.meta.module.ModulesMetadata;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

@BQConfig("Default JDBC DataSource configuration.")
@JsonDeserialize(using = DefaultDataSourceFactoryDeserializer.class)
public class DefaultManagedDataSourceFactory implements ManagedDataSourceFactory {

    private JsonNode jsonNode;

    public DefaultManagedDataSourceFactory(JsonNode jsonNode) {
        this.jsonNode = jsonNode;
    }

    @Override
    public ManagedDataSource createDataSource(String name, Injector injector, Collection<DataSourceListener> listeners) {
        return createDataSourceFactory(injector).createDataSource(name, injector, listeners);
    }

    private ManagedDataSourceFactory createDataSourceFactory(Injector injector) {

        ConfigObjectMetadata delegateFactoryType = delegateFactoryType(injector);
        JavaType jacksonType = TypeFactory.defaultInstance().constructType(delegateFactoryType.getType());
        ObjectMapper mapper = createObjectMapper(injector);
        JsonNode nodeWithType = jsonNodeWithType(delegateFactoryType.getTypeLabel());

        try {
            return mapper.readValue(new TreeTraversingParser(nodeWithType, mapper), jacksonType);
        } catch (IOException e) {
            throw new RuntimeException("Deserialization of JDBC DataSource configuration failed.", e);
        }
    }

    private JsonNode jsonNodeWithType(String type) {
        JsonNode copy = jsonNode.deepCopy();
        ((ObjectNode) copy).put("type", type);
        return copy;
    }

    private ObjectMapper createObjectMapper(Injector injector) {
        return injector.getInstance(JacksonService.class).newObjectMapper();
    }

    private ConfigObjectMetadata delegateFactoryType(Injector injector) {
        ConfigMetadataNode moduleConfig = moduleConfig(injector);

        ConfigObjectMetadata delegateFactoryType = moduleConfig.accept(new ConfigMetadataVisitor<ConfigObjectMetadata>() {

            @Override
            public ConfigObjectMetadata visitMapMetadata(ConfigMapMetadata metadata) {
                return delegateFactoryType((ConfigObjectMetadata) metadata.getValuesType());
            }
        });

        return Objects.requireNonNull(delegateFactoryType, "Can't find 'jdbc' configuration root");
    }

    private ConfigObjectMetadata delegateFactoryType(ConfigObjectMetadata factoryConfig) {

        Collection<ConfigMetadataNode> subtypes = factoryConfig.getSubConfigs();

        // will contain this class plus one or more concrete ManagedDataSourceFactory implementors. We can guess the
        // default only if there's a single implementor.

        switch (subtypes.size()) {
            case 0:
                // 0 is unexpected, but still report it as no DataSource implementations....
            case 1:
                // 1 means this class is the only implementor
                throw new IllegalStateException("No 'bootique-jdbc' implementations found. " +
                        "You will need to add one as an application dependency.");
            case 2:

                for (ConfigMetadataNode n : subtypes) {
                    if (!n.getType().equals(DefaultManagedDataSourceFactory.class)) {
                        return (ConfigObjectMetadata) n;
                    }
                }
                break;
            default:
                // > 2 means multiple implementors
                throw new IllegalStateException("Multiple bootique-jdbc implementations found. Each JDBC DataSource " +
                        "configuration must explicitly define \"type\" property.");
        }

        // should not get here under no circumstances ... if we did, likely bootique-jdbc code has diverged from the
        // assumptions in this method...
        throw new IllegalStateException("Internal error: Unexpected configuration structure in 'bootique-jdbc'");
    }

    private ConfigMetadataNode moduleConfig(Injector injector) {
        ModuleMetadata jdbcModule = moduleMetadata(injector);
        Collection<ConfigMetadataNode> configs = jdbcModule.getConfigs();

        if (configs.size() != 1) {
            throw new IllegalStateException("Expected a single root config in JdbcModule. Found: " + configs.size());
        }

        return configs.iterator().next();
    }

    // TODO: should this lookup be implemented in the metadata API?

    private ModuleMetadata moduleMetadata(Injector injector) {
        ModulesMetadata modulesMetadata = injector.getProvider(ModulesMetadata.class).get();

        for (ModuleMetadata md : modulesMetadata.getModules()) {
            if ("JdbcModule".equals(md.getName())) {
                return md;
            }
        }

        throw new IllegalStateException("JdbcModule is not present in runtime metadata");
    }
}
