package dev.omatheusmesmo.qlawkus.it;

import dev.langchain4j.agent.tool.Tool;
import dev.omatheusmesmo.qlawkus.tool.ClawTool;

@ClawTool
public class SampleExtensionTool {

  @Tool("A sample extension tool for testing")
  public String sampleExtensionTool(String input) {
    return "echo: " + input;
  }
}
