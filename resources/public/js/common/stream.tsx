import {baseUrl} from "../config/const";

export const getEvent = async () => {
    const response = await fetch(baseUrl + "stream/event", {
        headers: {
            "Content-Type": "application/json",
        },
        method: "GET",
        mode: "same-origin",
        redirect: "error",
    });
    const resp = await response.json();
    return resp;
};

export const getProp = async () => {
    const response = await fetch(baseUrl + "stream/prop", {
        headers: {
            "Content-Type": "application/json",
        },
        method: "GET",
        mode: "same-origin",
        redirect: "error",
    });
    const resp = await response.json();
    return resp;
};
