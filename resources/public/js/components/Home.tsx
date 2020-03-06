import React, { useState, useEffect, useRef, useContext } from "react";
import { Vote } from "./Vote";
import { baseUrl } from "../config/const";
import {EventScore, Leaderboard} from "./Leaderboard";
import { useInterval, getCSRFToken, scrollToTop } from "../common";
import { LoginContext } from "../contexts/LoginContext";
import { Bets } from "./Bets";
import { Link } from "react-router-dom";
import {Header} from "./Header";
import {Footer} from "./Footer";
import {getUser} from "../common/getUser";
import {getEvent, getProp} from "../common/stream";
import {Suggestion} from "./Suggestion";

const { install } = require('ga-gtag');

export const failedToFetch : string = "failed to fetch"

export function Home(props: any) {
  const [twitchUsername, setTwitchUsername] = useState<null | string>(null);
  const [matchName, setMatchName] = useState("");
  const [chatIsOpen, setChatIsOpen] = useState<boolean>(true);
  const [proposition, setProposition] = useState<any>(null);

  // child state
  const [eventScoreLeaderboard, setEventScoreLeaderboard] = useState<EventScore[]>([]);

  const isProduction: boolean = document.location.hostname.search("whiplashesports.com") !== -1;

  useEffect(() => {
    if (isProduction) {
      // Install Google tag manager
      install('UA-154430212-2')
    }

    getEvent().then((event) => {
      setTwitchUsername(event["event/twitch-user"] || failedToFetch)
      setMatchName(event["event/title"])
    });
    getProp().then((event) => {
      setProposition(event);
    });
  }, []);

  useInterval(() => {
    if (twitchUsername != failedToFetch) {
        getProp().then((event) => {
            setProposition(event);
        });
    }
  }, 3000);

  useInterval(() => {
    getEvent().then((event) => {
      setTwitchUsername(event["event/twitch-user"] || failedToFetch)
      setMatchName(event["event/title"])
    });
  }, 10000);

  const lastWinner = () => {
      const winner: EventScore = eventScoreLeaderboard[0];
      if (winner) {
          return winner.user_name
      }
      return "";
  };

  const renderContent = () => {
    // Loading
    if (twitchUsername == null) {
      return (
          <div className="twitch is-inactive">
            <div className="container">
              <h2 className="twitch__title">Loading...</h2>
              <div className="twitch__placeholder">
                <p className="twitch__subtitle">Hang tight, your event is loading.</p>
              </div>
            </div>
          </div>
      );
      // No stream to show
    } else if (twitchUsername == failedToFetch) {
      return (
          <div className="twitch is-inactive">
            <div className="container">
              <h2 className="twitch__title">Whiplash is taking a nap</h2>
                <div className="twitch__placeholder">
                    <p className="twitch__subtitle">Last Event Winner: {lastWinner()}</p>
                    <p>Hang tight, we'll have a watch party soon.</p>
                    <p>In the meantime, bookmark this page and check back often for new chances to win while watching.</p>
                </div>
            </div>
          </div>
      );
      // Found stream
    } else {
      return (
          <>
            <div className={"twitch" + (!chatIsOpen ? " chat-is-closed" : "")}>
              <header className="container twitch__header">
                <h2 className="twitch__title">{matchName}</h2>
                <button
                    className="button twitch__button"
                    type="button"
                    onClick={() => {
                      setChatIsOpen(!chatIsOpen)
                    }}>
                  {chatIsOpen ? 'Close Chat' : 'Open Chat'}
                </button>
              </header>
              <div className="aspect-ratio-wide twitch__video">
                <iframe
                    src={"https://player.twitch.tv/?channel=" + twitchUsername}
                    frameBorder="0"
                    allowFullScreen={true}>
                </iframe>
              </div>
                {chatIsOpen &&
                <div className="twitch__chat">
                    <iframe
                        frameBorder="0"
                        scrolling="true"
                        src={"https://www.twitch.tv/embed/" + twitchUsername + "/chat?darkpopout"}>
                    </iframe>
                </div>
                }
            </div>
            <Vote
                proposition={proposition}
                matchName={matchName}
                isProduction={isProduction}
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
                        twitchUsername={twitchUsername}
                        // matchID={matchID}
                        // currentGame={currentGame}
                    />
                    <Suggestion
                        twitchUsername={twitchUsername}/>
                    <Leaderboard
                        twitchUsername={twitchUsername}
                        proposition={proposition}
                        eventScoreLeaderboard={eventScoreLeaderboard}
                        setEventScoreLeaderboard={setEventScoreLeaderboard}
                    />
                </div>
            </main>
            <Footer/>
        </>
    );
}
