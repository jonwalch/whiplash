import React, { useEffect, useState, } from "react";
import "../../resources/public/css/App.css";
import {defaultLoggedIn, LoginContext} from "./contexts/LoginContext";
import {TwitchExtension} from "./components/TwitchExtension";

declare global {
    interface Window {
        Twitch:any;
    }
}

export const twitch = window.Twitch.ext;
export const twitchBaseUrl = process.env.NODE_ENV === 'development' ? 'http://localhost:3000/' : "https://whiplashesports.com/";
// This wasn't working
//export const OpaqueTwitchId =  process.env.NODE_ENV === 'development' ? 'testID123' : twitch.viewer.opaqueId;

export const CORSGetUser = async (loggedInState: any, setLoggedInState: Function) => {
    const response = await fetch(twitchBaseUrl + "user", {
        method: "GET",
        credentials: "omit",
        mode: "cors",
        redirect: "error",
        headers: {"x-twitch-opaque-id": process.env.NODE_ENV === 'development' ? 'testID123' : twitch.viewer.opaqueId}
    });
    if (response.status === 200) {
        const resp = await response.json();
        setLoggedInState({
            userName: resp["user/name"],
            status: resp["user/status"],
            cash: resp["user/cash"],
            notifications: resp["user/notifications"],
            "gated?": resp["user/gated?"]
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
        // TODO: update to gtag once twitch supports
        // Install google analytics inline for Twitch policy
        // @ts-ignore
        (function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){    (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),    m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m) })(window,document,'script','https://www.google-analytics.com/analytics.js','ga'); ga('create', 'UA-154430212-4', 'auto');

        // @ts-ignore
        ga('send', {hitType: 'pageview',});

        CORSGetUser(loggedInState,setLoggedInState);
    }, []);

    return (
        <LoginContext.Provider value={{loggedInState: loggedInState, setLoggedInState: setLoggedInState}}>
            <TwitchExtension />
        </LoginContext.Provider>
    );
};
