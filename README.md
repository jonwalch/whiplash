# whiplash

generated using Luminus version "3.48"

FIXME

## Prerequisites
Java 8+
Clojure 1.10.1
npm

You will need [Leiningen][1] 2.0 or above installed.

[1]: https://github.com/technomancy/leiningen


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

- `npm run develop`: Build CSS
- `npm run watch:css`: Build CSS and watch for changes (run this before `watch:js`)
- `npm run watch:js`: Build JS and watch for changes (run this after `watch:css` in another terminal tab/window)
- `npm run build`: Build CSS and JS, and minify CSS for production

### CSS

CSS source files are located in `/resources/css`, and will be processed via Gulp and output to `/resources/public/css/App.css`.

Use BEM methodology for naming component classes. Avoid element and ID selectors generally--use nested element selectors if/when it really makes sense. But probably just use classes instead. BEM stands for Block, Element, Modifier. The syntax looks like `.block__element--modifier`. For example, `.card`, `.card__title`, `.card--primary`, `.card__image--highlight`, etc. Components with compound words are hyphenated (not camelCased), like `.aspect-ratio` (not `.aspectRatio`).

To indicate state, use `.is-` and `.has-`, like `.is-visible` and `.has-loaded`. For JS hooks, use a `.js-` prefix, and don't use the `.js-` prefixed class for styling.

At some point it might make sense to move away from BEM in favor of a different CSS methodology, and that's fine, but stay consistent. If we're using BEM, use BEM. If we're switching to something else, update everything to that something else. Don't mix and match.

Feel free to use Sass-like syntax and dynamic imports. PostCSS does most of the heavy-lifting.

CSS gets inlined in `index.html`, which is fine for now. As the number of styles and pages grow, we will want to link styles in a cacheable `.css` file that gets served separately. Most important/'above the fold' styles can stay inline.

### JS

JS files are located in `/resources/public/js`.

## License

Copyright © 2019 FIXME

eksctl create cluster --name whiplash --version 1.14 --nodegroup-name standard-workers --node-type t3.medium --nodes 1 --nodes-min 1 --nodes-max 1 --node-ami auto

## If CloudFront isn't serving your newest content
aws cloudfront create-invalidation --distribution-id E451IY44F3ERG --paths "/*"
