import { check } from "k6";
import http from "k6/http";
import blockIds from "./data/block-ids.json";
import { randomElement } from "./utils/array-utils";
import env from "./env";
import { DEFAULT_OPTIONS } from "./constants";

export const options = DEFAULT_OPTIONS

export default () => {
  const id = randomElement(blockIds);

  const res = http.get(`${env.BASE_URL}/api/v1/blocks/${id}`);

  check(res, {
    "status is 200": () => res.status === 200,
    "id has the expected value": () => {
      if (typeof res.body === "string") {
        const body = JSON.parse(res.body);
        return body.id === id;
      } else {
        return false;
      }
    },
  });
};
