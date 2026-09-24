#!/usr/bin/env node
/**
 * Adds nginx SSI includes to the built index.html so every storefront URL is served with
 * its own title, description, canonical and a readable body — before any JavaScript runs.
 *
 * Why post-build: SSI directives are HTML comments, and CRA's minifier strips comments
 * from public/index.html. Run after `npm run build` (the Dockerfile does).
 *
 * What nginx gets:
 *   head: <!--# include virtual="/__seo/head$request_uri" stub="seo_head_default" -->
 *   body: <!--# include virtual="/__seo/body$request_uri" stub="seo_body_default" -->
 * The stubs hold the original static title/description, so a backend that is down, slow
 * or does not know the URL leaves the page exactly as it was before this script existed.
 *
 * Fails the build if a marker is missing: silently shipping without the includes would
 * look like success and quietly lose every page's server-side title.
 */
const fs = require('fs');

const file = process.argv[2] || 'build/index.html';
let html = fs.readFileSync(file, 'utf8');

function take(pattern, what) {
  const match = html.match(pattern);
  if (!match) {
    console.error(`inject-ssi: ${what} not found in ${file}`);
    process.exit(1);
  }
  return match[0];
}

const title = take(/<title>[^<]*<\/title>/, '<title>');
const description = take(/<meta name="description"[^>]*>/, 'meta description');
const root = take(/<div id="root"><\/div>/, '<div id="root"></div>');

if (html.includes('<!--# include')) {
  console.error(`inject-ssi: ${file} already has SSI includes`);
  process.exit(1);
}

// data-rh lets react-helmet-async replace the fallback description instead of adding a second one.
const fallbackDescription = description.replace('<meta ', '<meta data-rh="true" ');

html = html.replace(description, '');
html = html.replace(
  title,
  `<!--# block name="seo_head_default" -->${title}${fallbackDescription}<!--# endblock -->` +
    '<!--# include virtual="/__seo/head$request_uri" stub="seo_head_default" -->'
);
html = html.replace(
  root,
  '<div id="root"><!--# block name="seo_body_default" --><!--# endblock -->' +
    '<!--# include virtual="/__seo/body$request_uri" stub="seo_body_default" --></div>'
);

fs.writeFileSync(file, html);
console.log(`inject-ssi: SSI includes added to ${file}`);
