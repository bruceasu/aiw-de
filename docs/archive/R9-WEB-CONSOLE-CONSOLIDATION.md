# R9 — Web Console Consolidated Delivery

R9 consolidates the previously planned Web Console stages into one delivery on top of accepted R8-S13 Fix2.

## Included

- dashboard / run list / run detail / worker fleet / approvals / system views;
- autonomous requirement intake;
- pause / resume / recover / cancel;
- per-run runtime scheduling control;
- Fleet runtime scheduling control and audit history;
- Worker drain / offline;
- approval request / approve / reject;
- live polling that pauses while the user edits a form;
- optional Web Console session authentication;
- VIEWER / OPERATOR / APPROVER / ADMIN roles;
- per-session CSRF for browser mutations.

## Authority rule

The Web Console does not own business state. Mutation handlers call the existing application services directly. Existing task/run/approval/scheduling state machines remain authoritative.

## Persistence

No R9 business table or Flyway migration is added. Baseline remains 9 tables / V14.

## Security limitation

Web Console authentication protects `/ui/**`, not the pre-existing management REST APIs. The default bind stays `127.0.0.1`; remote deployments must provide network/reverse-proxy protection until management API auth/RBAC is implemented.
