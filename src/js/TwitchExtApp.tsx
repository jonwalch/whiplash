import React, { useEffect, useState, } from "react";
import "../../resources/public/css/App.css";
import {defaultLoggedIn, LoginContext} from "./contexts/LoginContext";
import {TwitchExtension} from "./components/TwitchExtension";

const { install } = require('ga-gtag');

declare global {
    interface Window {
        Twitch:any;
    }
}

export const twitch = window.Twitch.ext;
export const twitchBaseUrl = process.env.NODE_ENV === 'development' ? 'http://localhost:3000/' : "https://whiplashesports.com/";
export const OpaqueTwitchId =  process.env.NODE_ENV === 'development' ? 'testID123' : twitch.viewer.opaqueId;

export const CORSGetUser = async (loggedInState: any, setLoggedInState: Function) => {
    const response = await fetch(twitchBaseUrl + "user", {
        method: "GET",
        credentials: "omit",
        mode: "cors",
        redirect: "error",
        headers: {"x-twitch-opaque-id": OpaqueTwitchId}
    });
    if (response.status === 200) {
        const resp = await response.json();
        setLoggedInState({
            userName: resp["user/name"],
            status: resp["user/status"],
            cash: resp["user/cash"],
            notifications: resp["user/notifications"]
        });
        return resp["user/cash"] - loggedInState.cash;
    } else {
        setLoggedInState(defaultLoggedIn)
        return 0;
    }
};

export const TwitchExtApp = () => {
    const [loggedInState, setLoggedInState] = useState(defaultLoggedIn);

    useEffect(() => {
        // Install Google tag manager, will only track on hostnames that contain 'whiplash'
        // this was configured as a filter named 'whiplash hostname filter (filter out local host)'
        // in the GA admin panel
        install('UA-154430212-2');

        CORSGetUser(loggedInState,setLoggedInState);
    }, []);

    return (
        <LoginContext.Provider value={{loggedInState: loggedInState, setLoggedInState: setLoggedInState}}>
            <TwitchExtension />
        </LoginContext.Provider>
    );
};
