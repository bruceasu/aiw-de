import { register } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

if (process.env.FD005_TESTER_SESSION !== 'fd005-tester-20261005-7865d383' || !process.env.FD005_RUN_ROOT) throw new Error('Planner-authorized entrypoint required');
const repository = path.resolve(fileURLToPath(new URL('../../../', import.meta.url)));
register(new URL('./typescript-loader.mjs', import.meta.url), import.meta.url, { data: { repository } });
