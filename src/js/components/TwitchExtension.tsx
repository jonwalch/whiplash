import React, {useState, useEffect, } from "react";
import { useInterval } from "../common";
import {baseUrl} from "../config/const";
import "../../../resources/public/css/App.css";

declare global {
    interface Window {
        Twitch:any;
    }
}

const twitch = window.Twitch.ext;

export function TwitchExtension(props: any) {
    const [proposition, setProposition] = useState<any>({});
    // const [prevProposition, setPrevProposition] = useState<any>({});
    const [currentLeader, setCurrentLeader] = useState<string>("");
    const [extClass, setExtClass] = useState<string>("twitch-extension slide-out");
    const [extPositionClass, setExtPositionClass] = useState<string>("");

    const getCORSProp = async () => {
        const response = await fetch(baseUrl + "stream/prop", {
            method: "GET",
            credentials: "omit",
            mode: "cors",
            redirect: "error",
        });
        if (response.status === 200) {
            return await response.json();
        } else {
            return {}
        }
    };

    const getCORSEventLeaderboard = async () => {
        const response = await fetch(baseUrl + "leaderboard/event", {
            method: "GET",
            credentials: "omit",
            mode: "cors",
            redirect: "error",
        });
        if (response.status === 200) {
            return await response.json();
        } else {
            return []
        }
    };

    const getPropWrapper = (event:any) => {
        if (event["current-prop"]){
            setProposition(event["current-prop"]);
        } else {
            setProposition({});
        }
        // if (event["previous-prop"]) {
        //     setPrevProposition(event["previous-prop"]);
        // } else {
        //     setPrevProposition({});
        // }
    };

    const getEventLeaderboardWrapper = (event:any) => {
        if (event.length > 0){
            setCurrentLeader(event[0]["user_name"]);
        } else {
            setCurrentLeader("");
        }
    };

    useEffect(() => {
        getCORSProp().then((event) => {
            getPropWrapper(event)
        });

        getCORSEventLeaderboard().then((event) => {
            getEventLeaderboardWrapper(event)
        });

        twitch.configuration.onChanged(() => {
            let config = twitch.configuration.broadcaster ? twitch.configuration.broadcaster.content : "";

            try {
                config = JSON.parse(config)
            } catch (e) {
                config = ""
            }
            setExtPositionClass(config === "bottomleft" ? "is-bottom-left" : "");
        })

    }, []);

    useEffect( () => {
        if (Object.keys(proposition).length === 0 && proposition.constructor === Object) {
            setExtClass("twitch-extension slide-out " + extPositionClass)
        } else if (proposition["proposition/text"]) {
            setExtClass("twitch-extension slide-in " + extPositionClass)
        }
    }, [proposition])

    useInterval(() => {
        getCORSProp().then((event) => {
            getPropWrapper(event)
        });

        getCORSEventLeaderboard().then((event) => {
            getEventLeaderboardWrapper(event)
        });

    }, 10000);

    const renderPropositionText = () => {
        if (proposition["proposition/text"]) {
            return "Bet: " + proposition["proposition/text"];
        }
    };

    const renderContent = () => {
        return (
            // TODO: uninline styles
            <div className={extClass}>
                <img
                    src={baseUrl + "/img/logos/whiplash-horizontal-4c-gg.svg"}
                    alt="Whiplash"
                    width="165"
                    height="36"
                    className="site-logo"
                    style={{padding: "0.25rem", margin:"auto"}}
                />
                {currentLeader !== "" &&
                <p style={{fontSize: ".75rem", padding: "0 0.25rem 0.25rem 0.25rem", margin:"0", textAlign: "center"}}>
                    <span>1st place: </span><span style={{color: "gold"}}>{currentLeader}</span>
                </p>
                }
                <div style={{fontSize: ".75rem", padding: "0 0.5rem 0.5rem 0.5rem",}}>
                    {renderPropositionText()}
                </div>
            </div>
        );
    };

    return (
        <>
            {renderContent()}
        </>
    );
}
