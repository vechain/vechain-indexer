import {check} from "k6";
import http from "k6/http";
import accounts from "./data/clause-accounts.json";
import {randomElement} from "./utils/array-utils";
import env from "./env";

export const options = env.OPTIONS

/**
 *  Make a GET request to the transactions endpoint using a random address
 */
export default () => {
    const acct = randomElement(accounts);

    const res = http.get(`${env.BASE_URL}/api/v1/transactions/delegated?delegator=${acct}`);

    check(res, {
        "status is 200": () => res.status === 200,
        "has results": () => {
            if (typeof res.body === "string") {
                const body = JSON.parse(res.body);
                return body.data.length > 0;
            } else {
                return false;
            }
        },
    });
};
