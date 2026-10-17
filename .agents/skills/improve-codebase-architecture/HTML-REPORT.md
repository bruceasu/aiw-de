# Optional offline architecture report

Read only when an HTML presentation is requested. The default report remains
Markdown under [SKILL.md](SKILL.md). This file grants no browser, network,
rendering or implementation authority.

Write a unique `<workspace>/.ai/tmp/architecture-review-<timestamp>.html`, creating
that workspace directory if needed. Report the absolute path. Do not write to
the OS temporary directory, overwrite earlier reports, open the file, download
libraries or load resources from a CDN automatically.

Use one offline document with inline CSS and optional static SVG. No external
fonts, script imports, image URLs or network-dependent diagrams. Escape file
paths, code and other inserted text as HTML; do not embed executable project
content. If a diagram is useful, draw a small static before/after call or
responsibility view rather than adding a rendering dependency.

For each supported candidate include affected files, observed friction,
keeping-the-current-design option, proposed local change, any justified larger
alternative, benefits, compatibility/migration/verification risks, evidence
strength and next action. Recommendations must remain understandable without
the illustration. Flag relevant ADR conflicts with evidence rather than hiding
them in a diagram.

A minimal scaffold:

```html
<!doctype html>
<html lang="en">
<meta charset="utf-8">
<title>Architecture review</title>
<style>
body { max-width: 64rem; margin: 2rem auto; padding: 0 1rem;
       font: 1rem/1.6 system-ui; color: #172033; }
article { border: 1px solid #bcc6d4; padding: 1rem; margin: 1rem 0; }
pre { overflow: auto; white-space: pre-wrap; }
</style>
<main>
  <h1>Scoped architecture options</h1>
  <p>Scope, observation date and evidence limitations.</p>
  <article><h2>Candidate</h2><p>Evidence, options and trade-offs.</p></article>
  <h2>Recommendation and next action</h2>
</main>
</html>
```

Review the generated source and references statically. Do not claim the page
was opened, rendered or visually verified if those actions were not performed
under separate authority. Presentation completion does not approve a refactor.
