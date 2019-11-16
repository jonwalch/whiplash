import React, { useState, useEffect, useRef, useContext } from "react";
import { Login } from "./Login";
import { Vote } from "./Vote";
import { baseUrl } from "../config/const";
import { Leaderboard } from "./Leaderboard";
import { useInterval } from "../common";
import { LoginContext } from "../contexts/LoginContext";
import { Bets } from "./Bets";

declare const Twitch: any;

export interface Opponent {
  teamName: string;
  teamID: number;
}

export const defaultTeam : Opponent = { teamName: "", teamID: -1 }

const failedToFetch : string = "failed to fetch stream"

export function Home(props: any) {
  const { loggedInState, setLoggedInState } = useContext(LoginContext);
  const [team, setTeam] = useState<Opponent>(defaultTeam);
  const [streamURL, setURL] = useState("");
  const [twitchUsername, setTwitchUsername] = useState("");
  const [matchName, setMatchName] = useState("");
  const [matchID, setMatchID] = useState(0);
  const [currentGame, setCurrentGame] = useState<any>({});
  const [opponents, setOpponents] = useState<Opponent[]>([]);
  const [userStatus, setUserStatus] = useState<string | null>(null);
  const [passedGuessingPeriod, setPastGuessingPeriod] = useState<
    boolean | null
  >(null);

  useEffect(() => {
    getStream();
  }, []);

  useEffect(() => {
    if (twitchUsername) {
      twitchEmbed();
    }
  }, [twitchUsername]);

  useEffect(() => {
    if (loggedInState.userName) {
      getUser();
    } //teamName changes when a user makes a guess
  }, [loggedInState.userName, team.teamName]);

  const fifteenMinutes = 1000 * 60 * 15;
  useInterval(() => {
    //Allows if begin_at is null
    const beginAt: number = Date.parse(currentGame["begin_at"]);
    if (beginAt + fifteenMinutes <= Date.now()) {
      setPastGuessingPeriod(true);
    } else {
      setPastGuessingPeriod(false);
    }
  }, 1000);

  useInterval(() => {
    getStream();
  }, 10000);

  const getStream = async () => {
    const response = await fetch(baseUrl + "stream", {
      headers: { "Content-Type": "application/json" }
    });
    if (response.status == 200) {
      const resp = await response.json();
      console.log(resp);
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
      setURL(failedToFetch);
    }
  };

  const getUser = async () => {
    const response = await fetch(baseUrl + "user", {
      headers: { "Content-Type": "application/json" },
      method: "GET",
      mode: "same-origin",
      redirect: "error"
    });
    if (response.status == 200) {
      const resp = await response.json();
      console.log(resp);
      setUserStatus(resp["user/status"]);
      setLoggedInState({
        userName: loggedInState.userName,
        cash: resp["user/cash"]
      });
    } else {
      setUserStatus("");
    }
  };

  const twitchEmbed = () => {
    const node: any = document.getElementById("twitch-embed");
    if (node.firstChild) {
      node.removeChild(node.firstChild);
    }

    new Twitch.Embed("twitch-embed", {
      width: 1024,
      height: 576,
      channel: twitchUsername,
      autoplay: true,
      layout: "video-with-chat"
    });
  };

  const renderContent = () => {
    if (streamURL == "") {
      return <h3>Loading</h3>;
    } else if (streamURL == failedToFetch) {
    // } else if (false) {
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
          <div id="twitch-embed"></div>
          <Vote
            opponents={opponents}
            team={team}
            setTeam={setTeam}
            matchID={matchID}
            matchName={matchName}
            currentGame={currentGame}
            userStatus={userStatus}
            passedGuessingPeriod={passedGuessingPeriod}
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
      <div className="main-container">
        <Bets
          matchID={matchID}
          currentGame={currentGame}
          passedguessingPeriod={passedGuessingPeriod}
        />
        {renderContent()}
      </div>
      <Leaderboard />
    </div>
  );
}
