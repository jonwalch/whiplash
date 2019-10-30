#usage: must run form project root ./scripts/push-prod-dockerimage.sh 0.1.5
set -ev

npx webpack -p
lein uberjar
docker build -t whiplash:$1 .
docker tag whiplash:$1 296027954811.dkr.ecr.us-west-2.amazonaws.com/whiplash:$1

$(aws ecr get-login --no-include-email --region us-west-2)
docker push 296027954811.dkr.ecr.us-west-2.amazonaws.com/whiplash:$1

