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

    if (response.status === 200) {
        return await response.json();
    } else {
        return {}
    }
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
    if (response.status === 200) {
        return await response.json();
    } else {
        return {}
    }
};
