import { register } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

if (process.env.FD005_TESTER_SESSION !== 'fd005-tester-20261005-58dba13b' || !process.env.FD005_R19_SCRATCH) {
  throw new Error('FD-005 r19 requires the authorized offline runner');
}
const repository = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
register(new URL('./typescript-loader.mjs', import.meta.url), import.meta.url, { data: { repository } });
