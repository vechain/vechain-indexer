import { sleep, check } from "k6";
import { Options } from "k6/options";
import http from "k6/http";
import nftOwners from "./data/nft-owners.json";
import { randomElement } from "./utils/array-utils";

export let options: Options = {
  vus: 50,
  duration: "10s",
};

export default () => {
  const addr = randomElement(nftOwners);

  const res = http.get(`http://localhost:8080/api/v1/nfts/${addr}`);
  check(res, {
    "status is 200": () => res.status === 200,
  });

  check(res, {
    "list is not empty": () => !!res.body && res.body.length > 0,
  });

  sleep(1);
};
