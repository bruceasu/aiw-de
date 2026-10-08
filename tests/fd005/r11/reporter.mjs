// Keep raw Node coverage counts; do not infer business branches from scenario counts.
export default async function* report(source) {
  for await (const event of source) {
    if (!['test:pass', 'test:fail', 'test:coverage', 'test:summary', 'test:diagnostic'].includes(event.type)) continue;
    yield JSON.stringify(event, (_, value) => value instanceof Error
      ? { name: value.name, message: value.message, stack: value.stack, cause: value.cause }
      : typeof value === 'bigint' ? String(value) : value) + '\n';
  }
}



