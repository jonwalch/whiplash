import {baseUrl} from "../config/const";
import {defaultLoggedIn} from "../contexts/LoginContext";

export const logout = async (setLoggedInState: Function) => {
    const response = await fetch(baseUrl + "user/logout", {
        headers: {
            "Content-Type": "application/json",
        },
        method: "POST",
        mode: "same-origin",
        redirect: "error"
    });
    if (response.status == 200) {
        setLoggedInState(defaultLoggedIn);
    } else {
        alert("Failed to hit server to logout");
    }
};

