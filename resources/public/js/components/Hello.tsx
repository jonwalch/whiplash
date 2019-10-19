import React, { useState, useEffect } from "react";
declare const Twitch:any;

interface Opponent{
  teamName: string,
  teamID: number,
}

export function Hello(props: any) {
  const [team, setTeam] = useState<Opponent>({teamName: "", teamID: -1});
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

  useEffect(() => {
    if (twitchUsername) {
      twitchEmbed();
    }
  }, [twitchUsername]);

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
    console.log()

    let parsedOpponents : Opponent[] = [];
    resp["opponents"].forEach((element: any) => {
      parsedOpponents.push({ "teamID": element.opponent.id, "teamName": element.opponent.name });
    });
    setOpponents(parsedOpponents);
  };

  const handleClick = (team: Opponent) => {
    setTeam(team);
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
      {streamURL && (
        <div>
          <h3>{matchName}</h3>
          <div id="twitch-embed"></div>

          {/* <!-- Create a Twitch.Embed object that will render within the "twitch-embed" root element. --> */}
          {/* <iframe
              src={streamURL + "&muted=false"} //"https://player.twitch.tv/?channel=ramee&muted=false"
              height="576"
              width="1024"
              frameBorder="0"
              scrolling="no"
              allow="autoplay"
              allowFullScreen={true}
            ></iframe> */}
          <div>
              {opponents.map(opponent => {
                return (
                  <button
                    key = {opponent.teamID}
                    onClick={() =>
                      handleClick(opponent)
                    }
                  >
                    {opponent.teamName}
                  </button>
                );
              })}
            </div>
          <h1> You selected {team.teamName}</h1>
        </div>
      )}
    </div>
  );
}
