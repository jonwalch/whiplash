import {baseUrl} from "../config/const";

export const getUser = async (setLoggedInState: Function) => {
    const response = await fetch(baseUrl + "user", {
        headers: {"Content-Type": "application/json"},
        method: "GET",
        mode: "same-origin",
        redirect: "error"
    });
    if (response.status == 200) {
        const resp = await response.json();
        setLoggedInState({
            userName: resp["user/name"],
            status: resp["user/status"],
            cash: resp["user/cash"],
            notifications: resp["user/notifications"]
        });
    };
};
