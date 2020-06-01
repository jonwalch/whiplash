import React, {useState, useEffect, useContext} from "react";
import "../../../resources/public/css/App.css";

declare global {
    interface Window {
        Twitch:any;
    }
}

const twitch = window.Twitch.ext;

export function TwitchExtensionConfig(props: any) {
    const setTwitchConfig = (value : string) => {
        twitch.configuration.set("broadcaster", "0.1.3", JSON.stringify(value));
    };

    const renderContent = () => {
        return (
            <div className="form__button-group">
                <div>Select location for Whiplash overlay:</div>
                <button
                    className="button button--vote"
                    type="button"
                    key="tl"
                    onClick={() => {
                        setTwitchConfig("topleft")
                    }}>
                    Top Left
                </button>
                <button
                    className="button button--vote"
                    type="button"
                    key="bl"
                    onClick={() => {
                        setTwitchConfig("bottomleft")
                    }}>
                    Bottom Left
                </button>
            </div>
        );
    };

    return (
        <>
            {renderContent()}
        </>
    );
}
