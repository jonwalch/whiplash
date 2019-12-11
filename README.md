# whiplash

generated using Luminus version "3.48"

FIXME

## Prerequisites
- Java 8+
- Clojure 1.10.1
- [Leiningen][lein] 2.0 or above
- [npm][node]

[lein]: https://github.com/technomancy/leiningen
[node]: https://nodejs.org/en/download/

## Backend tests
Run from repl or:

    lein test

## Building prod docker images

    ./scripts/push-prod-dockerimage.sh x.x.x
    
## Deploying

    kubectl set image deployments/whiplash-web whiplash=296027954811.dkr.ecr.us-west-2.amazonaws.com/whiplash:x.x.x

## Running Locally

To start a web server for the application, run:

    lein run 

Or run in the REPL:

    (start-app nil)

## Front End Development

- `npm start`: Build CSS and JS, and minify CSS for production
- `npm run build`: Build CSS and JS, and minify CSS for production
- `npm test`: Run backend tests
- `npm run serve`: Build CSS and JS, and serve Whiplash to `localhost:3000`
- `npm run develop`: Build CSS and JS
- `npm run watch:css`: Build CSS and watch for changes (run this before `watch:js`)
- `npm run watch:js`: Build JS and watch for changes (run this after `watch:css` in another terminal tab/window)
- `npm run flush:cache`: Flush the Cloudfront CDN cache

### CSS

**Don't use inline styling**. **All styles should use class selectors** _(i.e., `.component { property: value; }`)_, and live in source files located in `/resources/css`. These will be processed via Gulp and output to `/resources/public/css/App.css`.

Use [BEM][bem] methodology for naming component classes and [ITCSS][itcss] for organizing styles. Avoid element and ID selectors generally--use nested element selectors only if/when it really makes sense. But probably just use classes instead. BEM stands for Block, Element, Modifier. The syntax looks like `.block__element--modifier`. For example, `.card`, `.card__title`, `.card--primary`, `.card__image--highlight`, etc. Components with compound words are hyphenated (not camelCased), like `.aspect-ratio` (not `.aspectRatio`).

To indicate state, use `.is-` and `.has-`, like `.is-visible` and `.has-loaded`. For JS hooks, use a `.js-` prefix, and don't use the `.js-` prefixed class for styling.

At some point it might make sense to move away from BEM in favor of a different CSS methodology, and that's fine, but stay consistent. If we're using BEM, use BEM. If we're switching to something else, update everything to that something else. Don't mix and match.

Feel free to use Sass-like syntax and dynamic imports. PostCSS does most of the heavy-lifting.

CSS gets inlined in `index.html`, which is fine for now. As the number of styles and pages grow, we will want to link styles in a cacheable `.css` file that gets served separately. Most important/'above the fold' styles can stay inline.

### JS

JS files are located in `/resources/public/js`. React powers the front end views as an SPA.

## License

Copyright © 2019 FIXME

eksctl create cluster --name whiplash --version 1.14 --nodegroup-name standard-workers --node-type t3.medium --nodes 1 --nodes-min 1 --nodes-max 1 --node-ami auto

## If CloudFront isn't serving your newest content
aws cloudfront create-invalidation --distribution-id E451IY44F3ERG --paths "/*"

[bem]: http://getbem.com/introduction/
[itcss]: https://speakerdeck.com/dafed/managing-css-projects-with-itcss