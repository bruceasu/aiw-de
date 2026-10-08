import { register } from 'node:module';
if (process.env.FD005_TESTER_SESSION !== 'fd005-tester-20261005-r39-f5c82a' || !process.env.FD005_R39_SCRATCH) {
  throw new Error('FD-005 r39 requires its authorized offline runner');
}
register(new URL('./typescript-loader.mjs', import.meta.url), import.meta.url);

