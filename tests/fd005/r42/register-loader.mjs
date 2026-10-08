import { register } from 'node:module';
if (process.env.FD005_TESTER_SESSION !== 'fd005-tester-20261005-r42-a9c4e2' || !process.env.FD005_R42_SCRATCH) {
  throw new Error('FD-005 r42 requires its authorized offline runner');
}
register(new URL('./typescript-loader.mjs', import.meta.url), import.meta.url);

