package dev.omatheusmesmo.qlawkus.tools.mcp.deployment;

import dev.omatheusmesmo.qlawkus.tools.mcp.McpAdminResource;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpConnection;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpConnectionStatus;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpManagementTool;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpRegistry;
import dev.omatheusmesmo.qlawkus.tools.mcp.McpToolContributor;
import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuth;
import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuthResource;
import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuthService;
import dev.omatheusmesmo.qlawkus.tools.mcp.auth.McpOAuthTool;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

/** The optional capability contributes runtime tools; upstream owns MCP protocol/native wiring. */
class McpClientProcessor {
  @BuildStep FeatureBuildItem feature() { return new FeatureBuildItem("qlawkus-mcp-client"); }

  @BuildStep AdditionalBeanBuildItem beans() {
    return AdditionalBeanBuildItem.builder().setUnremovable()
        .addBeanClasses(McpRegistry.class, McpToolContributor.class, McpManagementTool.class, McpAdminResource.class,
            McpOAuthService.class, McpOAuthResource.class, McpOAuthTool.class).build();
  }

  @BuildStep ReflectiveClassBuildItem metadata() {
    return ReflectiveClassBuildItem.builder(McpConnection.class, McpConnectionStatus.class, McpOAuth.class,
        McpOAuthService.LoginStart.class, McpOAuthService.AuthStatus.class, McpOAuthService.DisconnectStatus.class)
        .constructors().methods().fields().build();
  }
}
