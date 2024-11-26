FROM node:18-buster-slim as test

WORKDIR /var/task

# Install zip and create a zip package
RUN apt-get update && apt-get install -y yarn

# Copy the package.json and yarn.lock file into the docker image
COPY ["package.json","yarn.lock", "./"]

# Install dependencies
RUN yarn install --frozen-lockfile --production=false

# Copy context into the image
COPY [".", "./"]

RUN yarn build

RUN yarn test:js:unit


FROM node:18-buster-slim as gather

WORKDIR /var/task

# Install zip and create a zip package
RUN apt-get update && apt-get install -y yarn jq
COPY --from=timoreymann/deterministic-zip:latest /deterministic-zip /usr/bin/deterministic-zip

# Copy the Pipfile and Pipfile.lock file into the docker image
COPY --from=test ["/var/task/package.json","/var/task/yarn.lock", "/var/task/tsconfig.json", "./"]
COPY --from=test ["/var/task/src", "./"]

# Install dependencies, note that we adjust thor-devkit package.json to point to CJS definition instead of ESM, since it does not
# work for vanilla js
RUN yarn install --frozen-lockfile --production=true
RUN yarn global add typescript
RUN yarn add --dev @types/node

RUN yarn build

# select only few files
COPY --from=test ["/var/task/dist/src/lambda.js", "./"]

RUN deterministic-zip -r package.zip lambda.js
RUN echo foo

FROM scratch

#also, unit test results & code coverage can be exported in the same way
COPY --from=gather ["/var/task/package.zip", "/"]