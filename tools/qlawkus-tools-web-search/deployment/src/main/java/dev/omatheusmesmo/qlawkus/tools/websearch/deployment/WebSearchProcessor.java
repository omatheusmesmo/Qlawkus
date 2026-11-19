package dev.omatheusmesmo.qlawkus.tools.websearch.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;

/** Integrates the optional upstream SearXNG client, including Jackson DTOs in native images. */
class WebSearchProcessor {
    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem("qlawkus-web-search");
    }

    @BuildStep
    ServiceProviderBuildItem httpClientFactory() {
        return ServiceProviderBuildItem.allProvidersFromClassPath("dev.langchain4j.http.client.HttpClientBuilderFactory");
    }

    @BuildStep
    ReflectiveClassBuildItem searchResponse() {
        return ReflectiveClassBuildItem.builder(
                "dev.langchain4j.community.web.search.searxng.SearXNGResponse",
                "dev.langchain4j.community.web.search.searxng.SearXNGResult")
                .constructors().methods().fields().build();
    }
}
