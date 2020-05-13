import React, {useState, useEffect, useContext} from "react";
import { useInterval } from "../common";
import {baseUrl} from "../config/const";
import "../../../resources/public/css/App.css";

export function TwitchExtension(props: any) {
    const [proposition, setProposition] = useState<any>({});
    const [prevProposition, setPrevProposition] = useState<any>({});

    const getCORSProp = async () => {
        const response = await fetch(baseUrl + "stream/prop", {
            headers: {
                "Content-Type": "application/json",
            },
            method: "GET",
            credentials: "omit",
            mode: "cors",
            redirect: "error",
        });
        const resp = await response.json();
        return resp;
    };

    const getPropWrapper = (event:any) => {
        if (event["current-prop"]){
            setProposition(event["current-prop"]);
        } else {
            setProposition({});
        }
        if (event["previous-prop"]) {
            setPrevProposition(event["previous-prop"]);
        } else {
            setPrevProposition({});
        }
    };

    useEffect(() => {
        getCORSProp().then((event) => {
            getPropWrapper(event)
        });
    }, []);

    useInterval(() => {
        getCORSProp().then((event) => {
            getPropWrapper(event)
        });
    }, 3000);

    const renderPropositionText = () => {
        if (proposition["proposition/text"]) {
            return "Current Bet: " + proposition["proposition/text"];
        } else {
            return "Next proposition soon!";
        }
    };

    const renderContent = () => {
        return (
            // TODO: uninline styles
            <div style={{
                marginTop: "5rem",
                width: "15%",
                background: "hsla( 202, 65%, 3%, 0.35)",}}>
                <img
                    src={baseUrl + "/img/logos/whiplash-horizontal-4c-gg.svg"}
                    alt="Whiplash"
                    width="165"
                    height="36"
                    className="site-logo"
                    style={{padding: "0.25rem", margin:"auto"}}
                />
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
