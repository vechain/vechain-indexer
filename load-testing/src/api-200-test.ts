import { check } from "k6";
import { Options } from "k6/options";
import http from "k6/http";
import soloAccounts from "./data/solo-accounts.json";
import { randomElement } from "./utils/array-utils";
import env  from "./env";

export let options: Options = {
  stages: [
    { duration: '20s', target: 60 }, // simulate ramp-up of traffic from 1 to 60 users.
    { duration: '20s', target: 60 }, // stay at 60
    { duration: '20s', target: 100 }, // ramp-up to 100 users
    { duration: '20s', target: 100 }, // stay at 100 users for short amount of time
    { duration: '20s', target: 60 }, // ramp-down to 60 users
    { duration: '20s', target: 0 }, // ramp-down to 0 users
  ]
};

export default async () => {
  const addr = randomElement(soloAccounts);

  const res = http.get(`${env.BASE_URL}/api/v1/${env.API_RESOURCE}/${addr}`);

  check(res, {
    "status is 200": () => res.status === 200,
  });

  check(res, {
    "list is not empty": () => !!res.body && res.body.length > 0,
  });
};
