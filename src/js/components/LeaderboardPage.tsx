import React, {useState, useEffect} from "react";
import {Header} from "./Header";
import {Footer} from "./Footer";
import {EventScore, Leaderboard} from "./Leaderboard";
import {getEvent} from "../common/stream";
import {failedToFetch} from "./Home";

export function LeaderboardPage(props: any) {
    const [channelID, setChannelID] = useState<null | string>(null);
    const [eventScoreLeaderboard, setEventScoreLeaderboard] = useState<EventScore[]>([]);

    const lastWinner = () => {
        const winner: EventScore = eventScoreLeaderboard[0];
        if (winner) {
            return winner.user_name
        }
        return "";
    };

    useEffect(() => {
        getEvent().then((event) => {
            setChannelID(event["event/channel-id"] || failedToFetch);
        });

    }, []);

    return (
        <>
            <Header/>
            <p className="twitch__subtitle" style={{padding: "1rem", textAlign: "center"}}>
                Event Winner: {lastWinner()}
            </p>
            <Leaderboard
                noGrid={true}
                channelID={channelID}
                eventScoreLeaderboard={eventScoreLeaderboard}
                setEventScoreLeaderboard={setEventScoreLeaderboard}/>
            <Footer/>
        </>
    );
}