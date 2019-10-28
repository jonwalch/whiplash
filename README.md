# whiplash

generated using Luminus version "3.48"

FIXME

## Prerequisites

You will need [Leiningen][1] 2.0 or above installed.

[1]: https://github.com/technomancy/leiningen

## Building

In root dir:

    npx webpack -p
    lein uberjar
    docker build -t whiplash:0.1.0 .
    docker tag whiplash:0.1.0 296027954811.dkr.ecr.us-west-2.amazonaws.com/whiplash:0.1.0

    $(aws ecr get-login --no-include-email --region us-west-2)
    docker push 296027954811.dkr.ecr.us-west-2.amazonaws.com/whiplash:0.1.0

## Running

To start a web server for the application, run:

    lein run 

## License

Copyright © 2019 FIXME

eksctl create cluster --name whiplash --version 1.14 --nodegroup-name standard-workers --node-type t3.medium --nodes 1 --nodes-min 1 --nodes-max 1 --node-ami auto
