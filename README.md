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
    
#Deploying

    kubectl set image deployments/whiplash-web whiplash=296027954811.dkr.ecr.us-west-2.amazonaws.com/whiplash:x.x.x

## Running Locally

To start a web server for the application, run:

    lein run 

Or run in the REPL:

    (start-app nil)

## License

Copyright © 2019 FIXME

eksctl create cluster --name whiplash --version 1.14 --nodegroup-name standard-workers --node-type t3.medium --nodes 1 --nodes-min 1 --nodes-max 1 --node-ami auto

## If CloudFront isn't serving your newest content
aws cloudfront create-invalidation --distribution-id E451IY44F3ERG --paths "/*"
