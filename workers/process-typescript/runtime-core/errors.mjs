export class RuntimeFault extends Error {
  constructor(code, category = "TOOL_ERROR", fatal = true) {
    super(code);
    this.name = "RuntimeFault";
    this.code = code;
    this.category = category;
    this.fatal = fatal;
  }
}

export function checkSignal(signal) {
  if (signal?.aborted) throw signal.reason instanceof RuntimeFault ? signal.reason : new RuntimeFault("ATTEMPT_CANCELLED", "TOOL_ERROR");
}

export function failureCategory(error, phase = "TOOL_ERROR") {
  if (error instanceof RuntimeFault) return error.category === "TIMEOUT" ? "TOOL_ERROR" : error.category;
  const message = String(error);
  if (/\b429\b|rate.?limit/i.test(message)) return "RATE_LIMIT";
  if (/\b401\b|\b403\b|unauthori[sz]ed|no compatible approved/i.test(message)) return "CONFIGURATION";
  return phase;
}

export function errorCode(error) {
  // Never persist raw SDK errors: they can contain credentials, requests or source.
  return error instanceof RuntimeFault ? error.code : "OPERATION_FAILED";
}
