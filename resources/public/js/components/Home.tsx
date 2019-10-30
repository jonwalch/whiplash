import React, { useState, useEffect, useRef } from "react";
import { Login } from "./Login";
import { Vote } from "./Vote";
import { baseUrl } from "../config/const";
import { Leaderboard } from "./Leaderboard";
import { useInterval } from "../common";

declare const Twitch: any;

export interface Opponent {
  teamName: string;
  teamID: number;
}

export const defaultTeam : Opponent = { teamName: "", teamID: -1 }

const failedToFetch : string = "failed to fetch stream"

export function Home(props: any) {
  const [team, setTeam] = useState<Opponent>(defaultTeam);
  const [streamURL, setURL] = useState("");
  const [twitchUsername, setTwitchUsername] = useState("");
  const [matchName, setMatchName] = useState("");
  const [matchID, setMatchID] = useState(0);
  const [currentGame, setCurrentGame] = useState({});
  const [opponents, setOpponents] = useState<Opponent[]>([]);

  useEffect(() => {
    getStream();
  }, []);

  useInterval(() => {
    if (streamURL != "") {
      getStream();
    }
  }, 10000);

  // useEffect(() => {
  //   if (twitchUsername) {
  //     twitchEmbed();
  //   }
  // }, [twitchUsername]);

  const getStream = async () => {
    const response = await fetch( baseUrl + "stream", {
      headers: { "Content-Type": "application/json" }
    });
    if (response.status == 200) {
      const resp = await response.json();
      //console.log(resp);
      setURL(resp["live_url"]);
      setTwitchUsername(resp["twitch/username"]);
      setMatchName(resp["name"]);
      setMatchID(resp["id"]);
      setCurrentGame(resp["whiplash/current-game"]);

      let parsedOpponents: Opponent[] = [];
      resp["opponents"].forEach((element: any) => {
        parsedOpponents.push({
          teamID: element.opponent.id,
          teamName: element.opponent.name
        });
      });
      setOpponents(parsedOpponents);
    } else {
      //right now would be a 204
      setURL(failedToFetch)
    }
  };

  // const twitchEmbed = () => {
  //   new Twitch.Embed("twitch-embed", {
  //     width: 1024,
  //     height: 576,
  //     channel: twitchUsername
  //   });
  // };

  const renderContent = () => {
    if (streamURL == "") {
      return <h3>Loading</h3>;
    } else if (streamURL == failedToFetch) {
      return (
        <h3>
          Whiplash is taking a nap, hang tight, we'll find a CS:GO match for you
          soon.
        </h3>
      );
    } else {
      return (
        <div>
          <h3>{matchName}</h3>
          {/* <div id="twitch-embed"></div> */}
          <iframe
            src={streamURL + "&muted=false"} //"https://player.twitch.tv/?channel=ramee&muted=false"
            height="576"
            width="1024"
            frameBorder="0"
            scrolling="no"
            allow="autoplay"
            allowFullScreen={true}
          ></iframe>
          <Vote
            opponents={opponents}
            team={team}
            setTeam={setTeam}
            matchID={matchID}
            matchName={matchName}
            currentGame={currentGame}
          />
        </div>
      );
    }
  };

  //TODO size video based on web browser size
  return (
    <div>
      <h2>Whiplash (Pre-alpha) - Win While Watching</h2>
      <Login />
      {renderContent()}
      <Leaderboard/>
    </div>
  );
}
