import {check} from "k6";
import http from "k6/http";
import accounts from "./data/nft-accounts.json";
import {randomElement} from "./utils/array-utils";
import env from "./env";

// Select the option based on an environment variable


export const options = env.OPTIONS;

/**
 *  Make a GET request to the NFT endpoint using a random address
 */
export default () => {
    const account = randomElement(accounts);

    const response = http.get(`${env.BASE_URL}/api/v1/history/${account}`);

    check(response, {
        "status is 200": () => response.status === 200,
        "has results": () => {
            if (typeof response.body === "string") {
                const body = JSON.parse(response.body);
                return body.data.length > 0;
            } else {
                return false;
            }
        },
    });
};
