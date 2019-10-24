import React, { useState, useEffect } from "react";
import { Login } from "./Login";
import { Vote } from "./Vote";
declare const Twitch: any;

export interface Opponent {
  teamName: string;
  teamID: number;
}

export function Home(props: any) {
  const [team, setTeam] = useState<Opponent>({ teamName: "", teamID: -1 });
  const [streamURL, setURL] = useState("");
  const [twitchUsername, setTwitchUsername] = useState("");
  const [matchName, setMatchName] = useState("");
  const [beginAt, setBeginAt] = useState("");
  const [scheduledAt, setScheduledAt] = useState("");
  const [matchID, setMatchID] = useState(0);
  const [currentGame, setCurrentGame] = useState({});
  const [opponents, setOpponents] = useState<Opponent[]>([]);

  useEffect(() => {
    getStream();
  }, []);

  // useEffect(() => {
  //   if (twitchUsername) {
  //     twitchEmbed();
  //   }
  // }, [twitchUsername]);

  const getStream = async () => {
    const response = await fetch("http://localhost:3000/v1/stream", {
      headers: { "Content-Type": "application/json" }
    });
    const resp = await response.json();
    console.log(resp);
    setURL(resp["live_url"]);
    setTwitchUsername(resp["twitch/username"]);
    setMatchName(resp["name"]);
    setBeginAt(resp["begin_at"]);
    setScheduledAt(resp["scheduled_at"]);
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
  };

  const twitchEmbed = () => {
    new Twitch.Embed("twitch-embed", {
      width: 1024,
      height: 576,
      channel: twitchUsername
    });
  };

  //TODO size video based on web browser size
  return (
    <div>
      <h2>Whiplash - Win While Watching</h2>
      <Login />
      {streamURL && (
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
      )}
    </div>
  );
}
