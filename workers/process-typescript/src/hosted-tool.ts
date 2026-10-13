import { tool, type Tool } from "@openai/agents";
import { z } from "zod";
import type { ToolDefinition } from "../runtime-core/index.mjs";
import type { Host } from "./runtime-core.js";

/** Validate the original SDK input before its parser and account for every rejected call. */
export function createHostedTool(
  host: Host, description: string, parameters: z.ZodObject, definition: ToolDefinition,
): Tool {
  const sdkTool = tool({ name: definition.name, description, parameters, errorFunction: null,
    execute: async (args, _context, details) => await host.execute({ ...definition,
      validate: value => parameters.safeParse(value).success }, args, { callId: details?.toolCall?.callId }),
  });
  return { ...sdkTool, async invoke(context, input, details) {
    let original: unknown;
    try { original = JSON.parse(input); } catch { original = undefined; }
    if (!parameters.safeParse(original).success) {
      return await host.execute({ ...definition, validate: () => false }, original,
        { callId: details?.toolCall?.callId });
    }
    return await sdkTool.invoke(context, input, details);
  } };
}
