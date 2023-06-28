import {check} from "k6";
import http from "k6/http";
import accounts from "./data/nft-accounts.json";
import {randomElement} from "./utils/array-utils";
import env from "./env";
import {DEFAULT_OPTIONS} from "./constants";

export const options = DEFAULT_OPTIONS

/**
 *  Make a GET request to the NFT endpoint using a random address
 */
export default () => {
    const account = randomElement(accounts);

    const nfts = http.get(`${env.BASE_URL}/api/v1/nfts?address=${account}`);

    check(nfts, {
        "status is 200": () => nfts.status === 200,
        "has results": () => {
            if (typeof nfts.body === "string") {
                const body = JSON.parse(nfts.body);
                return body.data.length > 0;
            } else {
                return false;
            }
        },
    });
};
