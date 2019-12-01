import React, { useState, useEffect, useRef, useContext } from "react";
import { Login } from "./Login";
import { Signup } from "./Signup";
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

  //TODO revisit this, currently polling the user's cash and status every 10 seconds
  useInterval(() => {
    if (loggedInState.userName) {
      getUser();
    }
  }, 10000);

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
      return <p>Loading...</p>;
    } else if (streamURL == failedToFetch) {
    // } else if (false) {
      return (
        <>
          <h2>
            Whiplash is taking a nap
          </h2>
          <p>
            Hang tight, we'll find a CS:GO match for you soon.
          </p>
        </>
      );
    } else {
      return (
        <>
          <h2>{matchName}</h2>
          <div className="aspect-ratio-wide" id="twitch-embed"></div>
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
        </>
      );
    }
  };

  function scrollToTop() {
    window.scrollTo(0,0);
  }

  const [showSignup, setShowSignup] = useState(false);

  const renderSignup = () => {
    if (showSignup) {
      return (
        <>
          <Signup setShowSignup={setShowSignup}/>
        </>
      );
    }
  };

  return (
    <>
      <header role="banner" className="site-header">
        <div className="site-navigation container">
          <div className="site-branding">
            <h1 className="site-branding__title">
              <a href="/">
                <img
                  src="./img/logos/whiplash-horizontal-4c.svg"
                  alt="Whiplash"
                  width="165"
                  height="36"
                  className="site-logo"
                />
              </a>
            </h1>
          </div>
          <nav className="navigation">
            <ul className="navigation__list">
              <li><a className="navigation__link" href="/">About</a></li>
              <li><a className="navigation__link" href="mailto:support@whiplashesports.com">Contact</a></li>
            </ul>
          </nav>
          <nav className="navigation navigation--cta">
            <ul className="navigation__list">
              <li><button type="button" className="navigation__link">Login</button></li>
              <li>
                <button
                  type="button"
                  className="button"
                  onClick={() => {
                    setShowSignup(!showSignup);
                  }}
                >
                  Sign Up
                </button>
              </li>
            </ul>
          </nav>
        </div>
        {renderSignup()}
      </header>
      <Login />
      <main id="content" role="main" className="site-main">
        <div className="container">
          <Bets
            matchID={matchID}
            currentGame={currentGame}
            passedguessingPeriod={passedGuessingPeriod}
          />
          {renderContent()}
          <Leaderboard />
        </div>
      </main>
      <footer role="contentinfo" className="site-footer">
        <section className="site-navigation container">
          <div className="site-branding">
            <p className="site-branding__title">
              <a href="/">
                <img
                  src="./img/logos/whiplash-horizontal-4c.svg"
                  alt="Whiplash"
                  width="165"
                  height="36"
                  className="site-logo"
                />
              </a>
            </p>
          </div>
          <nav className="navigation">
            <ul className="navigation__list">
              <li><a className="navigation__link" href="/">About</a></li>
              <li><a className="navigation__link" href="mailto:support@whiplashesports.com">Contact</a></li>
            </ul>
          </nav>
          <nav className="navigation navigation--cta">
            <ul className="navigation__list">
              <li><button type="button" className="navigation__link">Login</button></li>
              <li>
                <button
                  type="button"
                  className="button"
                  onClick={() => {
                    scrollToTop();
                    setShowSignup(!showSignup);
                  }}
                >
                  Sign Up
                </button>
              </li>
            </ul>
          </nav>
        </section>
        <hr className="site-footer__hr" />
        <section className="container site-footer__content">
          <p><strong>Need help?</strong> Contact us at <a href="mailto:support@whiplashesports.com" target="_blank" rel="noreferrer">support@whiplashesports.com</a></p>
          <p>&copy; Whiplash. All Rights Reserved.</p>
          <p className="tagline">Win While Watching</p>
        </section>
      </footer>
    </>
  );
}
