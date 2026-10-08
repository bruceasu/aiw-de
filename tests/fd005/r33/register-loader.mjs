import { register } from 'node:module';
if (process.env.FD005_TESTER_SESSION !== 'fd005-tester-20261005-r33-7e49c2' || !process.env.FD005_R33_SCRATCH) {
  throw new Error('FD-005 r33 requires its authorized offline runner');
}
register(new URL('./typescript-loader.mjs', import.meta.url), import.meta.url);

