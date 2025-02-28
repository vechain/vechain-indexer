import {check} from "k6";
import http from "k6/http";
import accounts from "./data/nft-accounts.json";
import {randomElement} from "./utils/array-utils";
import env from "./env";

export const options = env.OPTIONS

/**
 *  Make a GET request to the contracts endpoint using a random address
 */
export default () => {
    const account = randomElement(accounts);

    const contracts = http.get(`${env.BASE_URL}/api/v1/contracts?address=${account}`);

    check(contracts, {
        "status is 200": () => contracts.status === 200,
        "has results": () => {
            if (typeof contracts.body === "string") {
                const body = JSON.parse(contracts.body);
                return body.data.length > 0;
            } else {
                return false;
            }
        },
    });
};
