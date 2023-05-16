import { check } from "k6";
import http from "k6/http";
import accounts from "./data/clause-accounts.json";
import { randomElement } from "./utils/array-utils";
import env from "./env";
import { DEFAULT_OPTIONS } from "./constants";

export const options = DEFAULT_OPTIONS

/**
 *  Make a GET request to the Clauses endpoint using a random address
 */
export default () => {
  const account = randomElement(accounts);

  const res = http.get(`${env.BASE_URL}/api/v1/clauses?address=${account}`);

  check(res, {
    "status is 200": () => res.status === 200,
    "has results": () => {
      if (typeof res.body === "string") {
        const body = JSON.parse(res.body);
        return body.length > 0;
      } else {
        return false;
      }
    },
  });
};
