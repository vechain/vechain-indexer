import {check} from "k6";
import http from "k6/http";
import {randomElement} from "./utils/array-utils";
import env from "./env";
import {DEFAULT_OPTIONS} from "./constants";

export const options = DEFAULT_OPTIONS

/**
 *  Make a GET request to the contracts endpoint using a random address
 */
export default () => {
    const contractType = randomElement(["VIP180", "VIP181", "VIP210", "ERC20", "ERC721", "ERC1155"])

    const contracts = http.get(`${env.BASE_URL}/api/v1/contracts?type=${contractType}`);

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
