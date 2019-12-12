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

Or run in the REPL from `env/dev/clj/user.clj`:

    (start)

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

- **Don't use inline styling**.
- **All styles (except the reset, globals, and elements) should use class selectors** _(i.e., `.component { property: value; }`)_
- **All styles should live in CSS source files** located in `/resources/css`. These will be processed via Gulp and output to `/resources/public/css/App.css`.
- Use [BEM][bem] methodology for naming component classes.
- Use [ITCSS][itcss] for organizing styles (specifically, we're using a reset, globals, elements, and components, in that order).
- Prioritize [keeping it simple with CSS that scales][css-scales].
- Use [Sass][sass] and dynamic imports. PostCSS is doing most of the heavy-lifting in the gulp pipeline to make this possible.
- **To indicate state**, use `.is-` and `.has-` _(i.e., `.is-visible` and `.has-loaded`)_.
- **For JS hooks**, use [`[data-]` attributes][data-attributes] _(i.e., `[data-modal-content]`)_.

Avoid element and ID selectors generally--use nested element selectors only if/when it really makes sense. But probably just use classes instead.

BEM stands for Block, Element, Modifier. The syntax looks like `.block__element--modifier`. For example, `.card`, `.card__title`, `.card--primary`, `.card__image--highlight`, etc. Components with compound words are hyphenated (not camelCased), like `.aspect-ratio` (not `.aspectRatio`). This allows teams of people to stay consistent with how they name classes. At some point it might make sense to move away from BEM in favor of a different CSS methodology, and that's fine, but stay consistent. If we're using BEM, use BEM. If we're switching to something else, update everything to use that something else. Don't mix and match naming methodologies.

TODO (paulshryock): CSS from `/resources/public/css/App.css` gets inlined in `index.html`, which is fine for now. Before a v1 launch, we should instead link styles in a cacheable `.css` file that gets served separately. Crucial 'above the fold' styles can stay in `/resources/public/css/App.css`.

### JS

JS files are located in `/resources/public/js`. React powers the front end views as an SPA.

## License

Copyright © 2019 FIXME

eksctl create cluster --name whiplash --version 1.14 --nodegroup-name standard-workers --node-type t3.medium --nodes 1 --nodes-min 1 --nodes-max 1 --node-ami auto

## If CloudFront isn't serving your newest content
aws cloudfront create-invalidation --distribution-id E451IY44F3ERG --paths "/*"

[bem]: http://getbem.com/introduction/
[itcss]: https://speakerdeck.com/dafed/managing-css-projects-with-itcss
[css-scales]: https://hankchizljaw.com/wrote/keeping-it-simple-with-css-that-scales/
[sass]: https://hankchizljaw.com/wrote/keeping-it-simple-with-css-that-scales/#heading-sass-for-the-win!
[data-attributes]: https://developer.mozilla.org/en-US/docs/Web/HTML/Global_attributes/data-*
