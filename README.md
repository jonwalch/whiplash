# whiplash

Whiplash was my first startup. Whiplash let Twitch viewers bet monopoly money on what happens next on stream. New bets pop up for every round of Counter-Stike:GO played (every two minutes). We automated the bets and their outcomes, using the CS:GO API. We also built a Twitch plug-in to drive more traffic to our site.

I built out our entire technical stack, closed our first 5 streamers, and discovered/delivered multiple growth channels that got us to 2000+ Monthly Active Users.

Tech stack: Clojure, TypeScript, React, Datomic Cloud, AWS Fargate, AWS CloudFront, Docker

generated using Luminus version "3.48"

FIXME

## Prerequisites
- Java 14
- Clojure 1.10.1
- [Leiningen][lein] 2.0 or above
- [npm][node]

[lein]: https://github.com/technomancy/leiningen
[node]: https://nodejs.org/en/download/

## Set up local DB
https://docs.datomic.com/cloud/dev-local.html

## Backend tests
Run from repl or:

    lein test

## Building prod docker images

    ./scripts/push-prod-dockerimage.sh x.x.x
    
## Deploying

    This is outdated
    fargate service deploy whiplash-web --image 296027954811.dkr.ecr.us-west-2.amazonaws.com/whiplash:x.x.x --cluster whiplash-cluster --region us-west-2

## Scale vertically

    fargate service update whiplash-web -c 2048 -m 8192 --cluster whiplash-cluster --region us-west-2

## Scale horizontally

    fargate service scale whiplash-web +2 --cluster whiplash-cluster --region us-west-2

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
- `npm run develop`: Build CSS and JS watch for changes
- `npm run flush:cache`: Flush the Cloudfront CDN cache

### CSS

- **Don't use inline styling**.
- **All styles (except settings, tools, generic, and base) should use class selectors** _(i.e., `.component { property: value; }`)_
- **All styles should live in CSS source files** located in `/resources/css`. These will be processed via Gulp and output to `/resources/public/css/App.css`.
- Use [BEM][bem] methodology for naming component classes.
- Use [ITCSS][itcss] for organizing styles. _(See `resources/css/style.css`.)_
- [Keep it simple with CSS that scales][simple-css-scales].
- Seriously, [write CSS that scales][css-scales].
- Avoid element and ID selectors for layouts and components--just use classes.
- Use [Sass][sass] and dynamic imports. PostCSS is doing most of the heavy-lifting in the gulp pipeline to make this possible.
- **To indicate state**, use `.is-` and `.has-` _(i.e., `.is-visible` and `.has-loaded`)_.
- **For JS hooks**, use [`[data-]` attributes][data-attributes] _(i.e., `[data-modal-content]`)_.

> The biggest, easiest win for organization and maintainability is to have a set of conventions in place that you and your team all follow... The important thing is that all of your CSS follows the conventions: that way, new (or forgetful) developers don’t need to learn the entire CSS codebase, only the conventions, to be able to contribute.
> --[Steve Grossi, How To Write CSS That Scales][css-scales]

TODO (paulshryock): CSS from `/resources/public/css/App.css` gets inlined in `index.html`, which is fine for now. Before a v1 launch, we should instead link styles in a cacheable `.css` file that gets served separately. Crucial 'above the fold' styles can stay in `/resources/public/css/App.css`.

### JS

JS files are located in `/resources/public/js`. React powers the front end views as an SPA.

## License

Copyright © 2019 FIXME

eksctl create cluster --name whiplash --version 1.14 --nodegroup-name standard-workers --node-type t3.medium --nodes 1 --nodes-min 1 --nodes-max 1 --node-ami auto

## If CloudFront isn't serving your newest content
aws cloudfront create-invalidation --distribution-id E451IY44F3ERG --paths "/*"

## Making Datomic Cloud talk to a fresh K8S cluster
Follow this https://docs.datomic.com/cloud/operation/client-applications.html#create-endpoint
Also make sure to set the proper inbound CIDR on the security group on the vpc endpoint (see original AWS support ticket from Nov).
The CIDR is found by looking at IPv4 CIDR on the actual VPC.

Configure EKS with a service account that has the proper perms:
https://docs.aws.amazon.com/eks/latest/userguide/enable-iam-roles-for-service-accounts.html
https://docs.aws.amazon.com/eks/latest/userguide/create-service-account-iam-policy-and-role.html

IAM policy:
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:GetObject"
            ],
            "Resource": [
                "arn:aws:s3:::prod-whiplash-datomic-storagef7f305e7-1-s3datomic-1ckyxqf9jsoux/*"
            ]
        }
    ]
}

eksctl create iamserviceaccount \
    --name datomic-s3 \
    --namespace default \
    --cluster prod \
    --attach-policy-arn arn:aws:iam::296027954811:policy/pod-datomic-s3 \
    --approve \
    --override-existing-serviceaccounts


OR attach the policy above directly to the node role `eksctl-prod-nodegroup-standard-wo-NodeInstanceRole-DE09WEOWWGS`
because datomic is using an old version of the AWS SDK that doesnt support service accounts yet


## Configuring NLB + nginx-ingress-controller
Use default yaml from K8S team here: https://kubernetes.github.io/ingress-nginx/deploy/
Add service.beta.kubernetes.io/aws-load-balancer-ssl-cert: $(CERT_ARN) to kind: Service


[bem]: http://getbem.com/introduction/
[itcss]: https://speakerdeck.com/dafed/managing-css-projects-with-itcss
[simple-css-scales]: https://hankchizljaw.com/wrote/keeping-it-simple-with-css-that-scales/
[css-scales]: https://work.stevegrossi.com/2014/09/06/how-to-write-css-that-scales/
[sass]: https://hankchizljaw.com/wrote/keeping-it-simple-with-css-that-scales/#heading-sass-for-the-win!
[data-attributes]: https://developer.mozilla.org/en-US/docs/Web/HTML/Global_attributes/data-*
