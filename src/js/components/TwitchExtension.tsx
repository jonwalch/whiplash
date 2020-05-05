import React, {useState, useEffect, useContext} from "react";
import { useInterval } from "../common";
import {baseUrl} from "../config/const";

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
                display: "flex",
                flexDirection: "row",
                justifyContent: "space-around",
                alignItems: "center",
                padding: "1rem 0 1rem 0"
            }}>
                <div>
                    <a href="https://whiplash.gg">
                        <img
                            src={baseUrl + "/img/logos/whiplash-horizontal-4c.svg"}
                            alt="Whiplash"
                            width="165"
                            height="36"
                            className="site-logo"
                        />
                    </a>
                    <div>Bet on <a href="https://whiplash.gg">whiplash.gg</a>!</div>
                </div>
                <div>{renderPropositionText()}</div>
            </div>
        );
    };

    return (
        <>
            {renderContent()}
        </>
    );
}
