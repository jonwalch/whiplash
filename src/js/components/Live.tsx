import React, {useState, useEffect, useContext} from "react";
import { useInterval } from "../common";
import {Header} from "./Header";
import {Footer} from "./Footer";
import {getAllEvents} from "../common/stream";
import {baseUrl} from "../config/const";

export function Live(props: any) {
    const [liveEvents, setLiveEvents] = useState<any[]>([]);

    const getAllEventsWrapper = () => {
        getAllEvents().then((events) => {
            setLiveEvents(events)
        })
    }

    useEffect(() => {
        getAllEventsWrapper()
    }, []);

    useInterval(() => {
        getAllEventsWrapper()
    }, 10000);

    const renderContent = () => {
        if (liveEvents.length > 0) {
            return (
                <div style={{
                    display: "flex",
                    flexWrap: "wrap"
                }}>
                    {liveEvents.map((event)=>{
                        return(
                            <button className="button"
                                    onClick={() => {
                                        window.location.href = baseUrl + "u/" + event["event/channel-id"]
                                    }}
                                    key={event["event/channel-id"]}
                                    style={{
                                        background: "hsl( 41, 94%, 51% )",
                                        width: "13rem",
                                        textAlign: "center",
                                        padding: "0.5rem",
                                        margin: "0.5rem",
                                        minHeight: "8rem",
                                        display: "flex",
                                        alignItems: "center",
                                        color: "unset",
                                    }}>
                                <div style={{width: "inherit"}}>
                                    <div style={{color: "hsl(220, 39%, 21%)"}}>
                                        {event["event/channel-id"]}
                                    </div>
                                    <div>{event["event/title"]}</div>
                                </div>
                            </button>
                        )
                    })}
                </div>
            );
        } else {
            return (
                <>
                    <h4 style={{textAlign: "center"}}>Whiplash is taking a nap</h4>
                    <div style={{display: "flex", justifyContent: "center", margin: "1.5rem", alignItems: "center"}}>
                        <h3 style={{margin: "0"}}>Join our</h3>
                        <button
                            type="button"
                            style={{margin: "0 0.5rem"}}
                            className="button navigation__button__tiny"
                            onClick={() => {
                                const win = window.open("https://discord.gg/GsG2G9t", '_blank');
                                // @ts-ignore
                                win.focus();
                            }}>
                            <img src={baseUrl + "/img/logos/Discord-Logo-Wordmark-White.svg"}/>
                        </button>
                        <h3 style={{margin: "0"}}>to find out when we're live next.</h3>
                    </div>
                </>
            );
        }
    }

    return (
        <>
            <Header sfx={false}/>
            <main id="content" role="main">
                <div className="home__layout__gridless">
                    <h1 style={{
                        textAlign: "center",
                        marginTop: "0.5rem",
                    }}>
                        Live Events
                    </h1>
                    {renderContent()}
                </div>
            </main>
            <Footer/>
        </>
    );
}
