import {check} from "k6";
import http from "k6/http";
import accounts from "./data/nft-accounts.json";
import {randomElement} from "./utils/array-utils";
import env from "./env";

export const options = env.OPTIONS

/**
 *  Make a GET request to the NFT contracts endpoint using a random address
 */
export default () => {
    const account = randomElement(accounts);

    const nftContracts = http.get(`${env.BASE_URL}/api/v1/nfts/contracts?owner=${account}`);

    check(nftContracts, {
        "status is 200": () => nftContracts.status === 200,
        "has results": () => {
            if (typeof nftContracts.body === "string") {
                const body = JSON.parse(nftContracts.body);
                return body.data.length > 0;
            } else {
                return false;
            }
        },
    });
};
