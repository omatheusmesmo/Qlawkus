package dev.omatheusmesmo.qlawkus.deployment;

import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;

import io.quarkiverse.langchain4j.QuarkusJsonCodecFactory;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chat message codec of quarkus-langchain4j reads the polymorphic type of a message and of its content
 * from annotations on private mixin classes. A native image drops those annotations for a class that is not
 * registered, so a stored message comes back with "missing type id property 'type'". This fails when the
 * mixins of the codec are not registered for reflection.
 */
class LangChain4jJsonMixinReflectionTest {

    private static final String CODEC = QuarkusJsonCodecFactory.class.getName();

    @Test
    void theMixinsCarryingTheTypeInfoOfMessagesAndContentsAreRegistered() throws Exception {
        Indexer indexer = new Indexer();
        for (String name : codecClassNames()) {
            try (InputStream in = QuarkusJsonCodecFactory.class.getClassLoader()
                    .getResourceAsStream(name.replace('.', '/') + ".class")) {
                indexer.index(in);
            }
        }
        Index index = indexer.complete();

        ReflectiveClassBuildItem registered = new ClientProcessor()
                .registerLangChain4jJsonMixinsForReflection(new CombinedIndexBuildItem(index, index));

        Set<String> names = new TreeSet<>(registered.getClassNames());
        assertTrue(names.contains(CODEC + "$ChatMessageMixin"), "ChatMessageMixin holds the message type info: " + names);
        assertTrue(names.contains(CODEC + "$ContentMixin"), "ContentMixin holds the content type info: " + names);
        assertTrue(registered.isConstructors() && registered.isMethods() && registered.isFields(),
                "Jackson reads constructors, methods and fields of a mixin");
    }

    private static Set<String> codecClassNames() {
        Set<String> names = new TreeSet<>();
        names.add(CODEC);
        for (Class<?> nested : QuarkusJsonCodecFactory.class.getDeclaredClasses()) {
            names.add(nested.getName());
        }
        names.add(CODEC + "$ChatMessageMixin");
        names.add(CODEC + "$ContentMixin");
        return names;
    }
}
