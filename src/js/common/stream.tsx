import {baseUrl} from "../config/const";

const bundleRegex = new RegExp('\\/dist\\/\\d?\\.{0,1}app\\.(.*)\\.js');

export const getEvent = async () => {
    // @ts-ignore "i dont care if this is null"
    const bundleContentHash = Array.from(document.scripts).filter(
        // @ts-ignore "i dont care if this is unknown"
        script => script.outerHTML.includes("/dist/app"))[0].outerHTML.match(bundleRegex)[1];

    const response = await fetch(baseUrl + "stream/event", {
        method: "GET",
        mode: "same-origin",
        redirect: "error",
        headers: {
            'Client-Version': bundleContentHash
        },
    });

    if (response.status === 200) {
        return await response.json();
    } else if (response.status === 205) {
        location.reload();
        return {};
    } else {
        return {}
    }
};

export const getProp = async () => {
    const response = await fetch(baseUrl + "stream/prop", {
        method: "GET",
        mode: "same-origin",
        redirect: "error",
    });
    if (response.status === 200) {
        return await response.json();
    } else {
        return {}
    }
};
