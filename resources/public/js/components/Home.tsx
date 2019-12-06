import React, { useState, useEffect, useRef, useContext } from "react";
import { Login } from "./Login";
import { Signup } from "./Signup";
import { Vote } from "./Vote";
import { baseUrl } from "../config/const";
import { Leaderboard } from "./Leaderboard";
import { useInterval, getCSRFToken } from "../common";
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

  const loggedIn = async () => {
    const response = await fetch(baseUrl + "user/login", {
      headers: { "Content-Type": "application/json" },
      method: "GET",
      mode: "same-origin",
      redirect: "error"
    });

    if (response.status == 200){
      const resp = await response.json();
      setLoggedInState({ userName: resp["user/name"], cash: loggedInState.cash});
      setShowSignup(false);
    } else {
      setLoggedInState({ userName: "", cash: loggedInState.cash})
    }
  };

  useEffect(() => {
    getStream();
    loggedIn();
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

  // const fifteenMinutes = 1000 * 60 * 15;
  // useInterval(() => {
  //   //Allows if begin_at is null
  //   const beginAt: number = Date.parse(currentGame["begin_at"]);
  //   if (beginAt + fifteenMinutes <= Date.now()) {
  //     setPassedGuessingPeriod(true);
  //   } else {
  //     setPassedGuessingPeriod(false);
  //   }
  // }, 1000);

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

    const options = {
      width: 1024,
      height: 576,
      channel: twitchUsername,
      autoplay: true
    }

    const player = new Twitch.Embed("twitch-embed", options);
  };

  const renderContent = () => {
    if (streamURL == "") {
      return (
        <div className="twitch">
          <div className="container">
            <p className="twitch__title">Loading...</p>
          </div>
        </div>
      );
    } else if (streamURL == failedToFetch) {
    // } else if (false) {
      return (
        <div className="twitch">
          <div className="container">
            <h2 className="twitch__title">Whiplash is taking a nap</h2>
            <p>Hang tight, we'll find a CS:GO match for you soon.</p>
          </div>
        </div>
      );
    } else {
      return (
        <>
          <Vote
            opponents={opponents}
            team={team}
            setTeam={setTeam}
            matchID={matchID}
            matchName={matchName}
            currentGame={currentGame}
            userStatus={userStatus}
          />
          <div className="twitch">
            <div className="twitch__embed" id="twitch-embed"></div>
          </div>
        </>
      );
    }
  };

  function scrollToTop() {
    window.scrollTo(0,0);
  }

  const [showLogin, setShowLogin] = useState(false);
  const renderLogin = () => {
    if (showLogin) {
      return (
        <Login setShowLogin={setShowLogin}/>
      );
    }
  };

  const [showSignup, setShowSignup] = useState(false);
  const renderSignup = () => {
    if (showSignup) {
      return (
        <Signup setShowSignup={setShowSignup}/>
      );
    }
  };

  const logout = async () => {
    const response = await fetch(baseUrl + "user/logout", {
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-Token": getCSRFToken()
      },
      method: "POST",
      mode: "same-origin",
      redirect: "error"
    });
    const resp = await response.json();
    console.log(resp);
    console.log(response.status);
    if (response.status == 200) {
      setLoggedInState({userName: "", cash: 0});
    } else {
      alert("Failed to hit server to logout");
    }
  };

  const renderLogInOutButton = () => {
    if (loggedInState.userName === null) {
      // No userName
      return
    } else if (loggedInState.userName === '') {
      // Show log in button
      return (
        <li>
          <button
            type="button"
            className="navigation__link"
            onClick={() => {
              scrollToTop();
              setShowLogin(!showLogin);
            }}
          >
            Log In
          </button>
        </li>
      )
    } else {
      // Show log out button
      return (
        <>
          <li className="navigation__item">{loggedInState.userName}</li>
          <li className="navigation__item"><span className="navigation__highlight">Cash:</span> ${loggedInState.cash}</li>
          <li>
            <button
              type="button"
              className="navigation__link"
              onClick={() => {
                  logout()
                  setShowLogin(false);
                }
              }
            >
              Log Out
            </button>
          </li>
        </>
      )
    }
  }

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
              {renderLogInOutButton()}
              <li>
                <button
                  type="button"
                  className="button navigation__button"
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
        </div>
        {renderLogin()}
        {renderSignup()}
      </header>
      <main id="content" role="main" className="site-main">
        {renderContent()}
        <Bets
          matchID={matchID}
          currentGame={currentGame}
        />
        <Leaderboard />
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
              {renderLogInOutButton()}
              <li>
                <button
                  type="button"
                  className="button navigation__button"
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
          <p>&copy; Whiplash. All Rights Reserved.</p>
          <p><strong>Need help?</strong> Contact us at <a href="mailto:support@whiplashesports.com" target="_blank" rel="noreferrer">support@whiplashesports.com</a></p>
        </section>
      </footer>
    </>
  );
}
