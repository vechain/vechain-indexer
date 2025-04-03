FROM vechain/thor:v2.2.1

USER root

RUN apk update && apk upgrade && apk add curl

USER thor

ENTRYPOINT thor solo --on-demand \
    --persist \
    --data-dir /data/thor \
    --api-addr 0.0.0.0:8669 \
    --api-cors '*' \
    --verbosity 3
