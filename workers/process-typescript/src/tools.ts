import type { Tool } from "@openai/agents";
import { z } from "zod";
import { lstat, mkdir, readFile, readdir } from "node:fs/promises";
import path from "node:path";
import { RuntimeFault, atomicWrite, editFile, runCommand, safePath, sha256, utf8Bytes, type Host } from "./runtime-core.js";
import type { ToolDefinition } from "../runtime-core/index.mjs";
import { createHostedTool } from "./hosted-tool.js";

export interface ToolContext {
  cwd: string; host: Host; readOnly: boolean; allowShell: boolean; toolTimeoutMs: number; maxOutputBytes: number;
}

/** SDK scheduling stays parallel; every primitive enters the same host. */
export function createWorkspaceTools(ctx: ToolContext): Tool[] {
  function primitive<P extends z.AnyZodObject>(description: string, parameters: P, definition: ToolDefinition): Tool {
    return createHostedTool(ctx.host, description, parameters, definition);
  }
  const tools: Tool[] = [primitive("Read a UTF-8 workspace file by line range. Lines start at 1; the JSON envelope describes completeness.",
    z.object({ path: z.string(), offset: z.number().int().positive(), limit: z.number().int().positive().max(1000) }).strict(), {
      name: "read", file: true,
      async run(args, { file, signal }) {
        if ((await lstat(file!)).size > 2 * 1024 * 1024) throw new RuntimeFault("READ_FILE_TOO_LARGE", "TOOL_ERROR", false);
        const text = await readFile(file!, { encoding: "utf8", signal });
        const lines = text.split(/\r?\n/);
        return { content: lines.slice(args.offset - 1, args.offset - 1 + args.limit).map((line, i) => `${args.offset + i}: ${line}`).join("\n"),
          data: { offset: args.offset, requestedLines: args.limit, totalLines: lines.length, sourceDigest: sha256(text) } };
      },
    }), primitive("List visible workspace entries. Hidden paths, dependencies and output directories are outside this listing scope.",
    z.object({ path: z.string() }).strict(), {
      name: "ls", resource: { mode: "S" },
      async run(args) {
        const entries = await readdir(await safePath(ctx.cwd, args.path), { withFileTypes: true });
        return entries.filter(entry => visible(entry.name) && !entry.isSymbolicLink()).map(entry => `${entry.name}${entry.isDirectory() ? "/" : ""}`).sort().join("\n");
      },
    }), primitive("Search literal text in visible UTF-8 files. Bounded scanning reports incomplete results explicitly; it excludes hidden/dependency/output paths.",
    z.object({ path: z.string(), text: z.string().min(1) }).strict(), {
      name: "grep", resource: { mode: "S" },
      async run(args, { signal }) {
        const matches: string[] = [];
        let scanned = 0;
        let outputBytes = 0;
        let complete = true;
        let stopped = false;
        const deadline = Date.now() + ctx.toolTimeoutMs;
        async function visit(directory: string): Promise<void> {
          for (const entry of await readdir(directory, { withFileTypes: true })) {
            signal.throwIfAborted();
            if (++scanned > 5000 || outputBytes >= ctx.maxOutputBytes || Date.now() >= deadline) { complete = false; stopped = true; return; }
            if (!visible(entry.name)) continue;
            if (entry.isSymbolicLink()) { complete = false; continue; }
            const file = await safePath(ctx.cwd, path.relative(ctx.cwd, path.join(directory, entry.name)));
            if (entry.isDirectory()) { await visit(file); if (stopped) return; }
            else if (entry.isFile()) {
              if ((await lstat(file)).size > 512 * 1024) { complete = false; continue; }
              const content = await readFile(file, { encoding: "utf8", signal });
              if (content.includes("\0")) { complete = false; continue; }
              const lines = content.split(/\r?\n/);
              for (let i = 0; i < lines.length; i++) {
                if (!lines[i]!.includes(args.text)) continue;
                const match = `${path.relative(ctx.cwd, file)}:${i + 1}: ${lines[i]}`;
                matches.push(match);
                outputBytes += utf8Bytes(match) + 1;
                if (outputBytes >= ctx.maxOutputBytes) { complete = false; stopped = true; return; }
              }
            }
          }
        }
        await visit(await safePath(ctx.cwd, args.path));
        return { content: matches.join("\n"), complete, data: { scanned, scope: "visible UTF-8 files <=512KiB", scanComplete: complete } };
      },
    })];
  if (ctx.readOnly) return tools;
  tools.push(primitive("Create or overwrite one UTF-8 workspace file atomically (2 MiB limit).", z.object({ path: z.string(), content: z.string() }).strict(), {
    name: "write", file: true, write: true,
    async run(args, { file, signal }) {
      if (utf8Bytes(args.content) > 2 * 1024 * 1024) throw new RuntimeFault("WRITE_FILE_TOO_LARGE", "TOOL_ERROR", false);
      await mkdir(path.dirname(file!), { recursive: true });
      await safePath(ctx.cwd, args.path, true);
      await atomicWrite(file!, args.content, signal);
      return { content: "File written", data: { resultDigest: sha256(args.content) } };
    },
  }), primitive("Replace one exact unique occurrence. Match normalization preserves BOM and the file's newline style.",
    z.object({ path: z.string(), oldText: z.string().min(1), newText: z.string() }).strict(), {
      name: "edit", file: true, write: true,
      async run(args, { file, signal }) {
        const result = await editFile(file!, [{ oldText: args.oldText, newText: args.newText }], signal);
        return { content: result.text, complete: result.complete, data: { edits: result.count, resultDigest: result.digest } };
      },
    }), primitive("Apply multiple exact unique, disjoint replacements against one original baseline. All matches are validated before one atomic write.",
    z.object({ path: z.string(), edits: z.array(z.object({ oldText: z.string().min(1), newText: z.string() }).strict()).min(1).max(128) }).strict(), {
      name: "edit_many", file: true, write: true,
      async run(args, { file, signal }) {
        const result = await editFile(file!, args.edits, signal);
        return { content: result.text, complete: result.complete, data: { edits: result.count, resultDigest: result.digest } };
      },
    }));
  if (ctx.allowShell) tools.push(primitive("Run an operator-authorized foreground command using PowerShell on Windows or sh elsewhere. Host permissions apply; workspace tools pause for this command. Cancellation terminates the process tree/group where supported.",
    z.object({ command: z.string().min(1) }).strict(), {
      name: "bash", shell: true, write: true, resource: { mode: "X" },
      async run(args, { signal }) {
        const windows = process.platform === "win32";
        const result = await runCommand(windows ? "powershell.exe" : "/bin/sh",
          windows ? ["-NoProfile", "-NonInteractive", "-Command", args.command] : ["-c", args.command],
          { cwd: ctx.cwd, signal, timeoutMs: ctx.toolTimeoutMs, maxBytes: ctx.maxOutputBytes, allowFailure: true });
        return { content: `${result.stdout}\n${result.stderr}`, mode: "middle", complete: result.complete,
          status: result.exitCode === 0 ? "ok" : "error", data: { exitCode: result.exitCode, processTree: result.processTree } };
      },
    }));
  return tools;
}

function visible(name: string): boolean {
  return !name.startsWith(".") && !["node_modules", "dist", "out", "target", "__pycache__"].includes(name);
}
