import React, { useState, useEffect, useRef, useContext } from "react";
import { Login } from "./Login";
import { Signup } from "./Signup";
import { Vote } from "./Vote";
import { baseUrl } from "../config/const";
import { Leaderboard } from "./Leaderboard";
import { useInterval, getCSRFToken, scrollToTop } from "../common";
import { LoginContext } from "../contexts/LoginContext";
import { Bets } from "./Bets";
import { Link } from "react-router-dom";
import {Header} from "./Header";
import {Footer} from "./Footer";

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
  const [matchID, setMatchID] = useState(-1);
  const [currentGame, setCurrentGame] = useState<any>({});
  const [opponents, setOpponents] = useState<Opponent[]>([]);
  const [userStatus, setUserStatus] = useState<string | null>(null);

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

  useInterval(() => {
    getStream();
    //TODO revisit this, currently polling the user's cash and status every 10 seconds
    if (loggedInState.userName) {
      getUser();
    }
  }, 10000);

  const getStream = async () => {
    const response = await fetch(baseUrl + "stream", {
      headers: { "Content-Type": "application/json" }
    });
    if (response.status == 200) {
      const resp = await response.json();
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
      setTwitchUsername("");
      setMatchName("");
      setMatchID(-1);
      setCurrentGame({});
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
      setUserStatus(resp["user/status"]);
      setLoggedInState({
        userName: resp["user/name"],
        cash: resp["user/cash"]
      });
    } else {
      setUserStatus("");
    }
  };

  const twitchEmbed = () => {
    const node: any = document.querySelector('#twitch-embed');
    const hasNode = node !== null;
    if (hasNode && node.firstChild) {
      node.removeChild(node.firstChild);
    }

    const options = {
      width: 1024,
      height: 576,
      channel: twitchUsername,
      autoplay: true
    };

    if (hasNode) {
      const player = new Twitch.Embed("twitch-embed", options);
    }
  };

  const renderContent = () => {
    // Loading
    if (streamURL == "") {
      return (
        <div className="twitch is-inactive">
          <div className="container">
            <h2 className="twitch__title">Loading...</h2>
            <div className="twitch__placeholder">
              <div className="container">
                <p className="twitch__subtitle">Hang tight, your CS:GO match is loading.</p>
              </div>
            </div>
          </div>
        </div>
      );
    // No stream to show
    } else if (streamURL == failedToFetch) {
      return (
        <div className="twitch is-inactive">
          <div className="container">
            <h2 className="twitch__title">Whiplash is taking a nap</h2>
            <div className="twitch__placeholder">
              <div className="container">
                <p className="twitch__subtitle">Hang tight, we'll find a CS:GO match for you soon.</p>
                <p>In the meantime, bookmark this page and check back often for new chances to win while watching.</p>
              </div>
            </div>
          </div>
        </div>
      );
    // Found stream
    } else {
      return (
        <>
          <div className="twitch">
            <header className="container">
              <h2 className="twitch__title">{matchName}</h2>
            </header>
            <div className="twitch__embed" id="twitch-embed"></div>
          </div>
          <Vote
            opponents={opponents}
            team={team}
            setTeam={setTeam}
            matchID={matchID}
            matchName={matchName}
            currentGame={currentGame}
            userStatus={userStatus}
          />
        </>
      );
    }
  };

  return (
      <>
        <Header/>
        <main id="content" role="main">
          <div className="home__layout">
            {renderContent()}
            <Bets
              matchID={matchID}
              currentGame={currentGame}
            />
            <Leaderboard />
          </div>
        </main>
        <Footer/>
      </>
  );
}
