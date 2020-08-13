import React, { useEffect, } from "react";
import { useInterval } from "../common";
import {Header} from "./Header";
import {Footer} from "./Footer";
import {getAllEvents} from "../common/stream";
import {baseUrl} from "../config/const";
import {Landing} from "./Landing";

export function Home(props: any) {
    const redirectIfEvent = () => {
        getAllEvents().then((events) => {
            const randomEvent = events[Math.floor(Math.random() * events.length)];
            if (randomEvent && Object.keys(randomEvent).length !== 0 && randomEvent.constructor === Object) {
                const chanID = randomEvent["event/channel-id"]
                if (chanID) {
                    window.location.href = baseUrl + "u/" + chanID
                }
            }
        })
    }

    useEffect(() => {
        redirectIfEvent()
    }, []);

  useInterval(() => {
      redirectIfEvent()
  }, 10000);

    return (
        <>
            <Header sfx={false}/>
            <main id="content" role="main">
                <div className="home__layout__gridless">
                    <Landing/>
                </div>
            </main>
            <Footer/>
        </>
    );
}
