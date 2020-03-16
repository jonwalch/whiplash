import React, { useState, useEffect } from "react";
import { Vote } from "./Vote";
import {EventScore, Leaderboard} from "./Leaderboard";
import { useInterval } from "../common";
import { Bets } from "./Bets";
import {Header} from "./Header";
import {Footer} from "./Footer";
import {getEvent, getProp} from "../common/stream";
import {Suggestion} from "./Suggestion";

const { install } = require('ga-gtag');

export const failedToFetch : string = "failed to fetch"

export function Home(props: any) {
  const [channelID, setChannelID] = useState<null | string>(null);
  const [matchName, setMatchName] = useState<string>("");
  const [streamSource, setStreamSource] = useState<string>("");
  const [chatIsOpen, setChatIsOpen] = useState<boolean>(false);
  const [proposition, setProposition] = useState<Object>({});
  const [prevProposition, setPrevProposition] = useState<Object>({});

  // child state
  const [eventScoreLeaderboard, setEventScoreLeaderboard] = useState<EventScore[]>([]);

  const isProduction: boolean = document.location.hostname.search("whiplashesports.com") !== -1;

  const getEventWrapper = (event:any) => {
      setChannelID(event["event/channel-id"] || failedToFetch);
      setMatchName(event["event/title"]);
      setStreamSource(event["event/stream-source"]);
    };

  const getPropWrapper = (event:any) => {
      if (event["current-prop"]){
          setProposition(event["current-prop"]);
      } else {
          setProposition({});
      }
      if (event["previous-prop"]) {
          setPrevProposition(event["previous-prop"]);
      } else {
          setPrevProposition({});
      }
  };

    useEffect(() => {
        if (isProduction) {
      // Install Google tag manager
      install('UA-154430212-2')
    }

    getEvent().then((event) => {
        getEventWrapper(event)
    });

    getProp().then((event) => {
        getPropWrapper(event)
    });
  }, []);

  useInterval(() => {
    if (channelID != failedToFetch) {
        getProp().then((event) => {
            getPropWrapper(event)
        });
    }
  }, 3000);

  useInterval(() => {
    getEvent().then((event) => {
        getEventWrapper(event)
    });
  }, 10000);

  const lastWinner = () => {
      const winner: EventScore = eventScoreLeaderboard[0];
      if (winner) {
          return winner.user_name
      }
      return "";
  };

  const streamSourceToStreamUrl = () => {
      if (streamSource == "event.stream-source/cnn-unauth") {
          return "https://fave.api.cnn.io/v1/fav/?video=cvplive/cvpstream0&customer=cnn&edition=domestic&env=prod&isLive=true";
      }
      else if (streamSource == "event.stream-source/youtube") {
          return "https://www.youtube.com/embed/live_stream?channel=" + channelID;
      }
      else if (streamSource == "event.stream-source/twitch") {
         return "https://player.twitch.tv/?channel=" + channelID;
      }
  };

    const streamSourceToChatUrl = () => {
        // TODO: youtube live chat seems to need the video id, not the channel id
        if (streamSource == "event.stream-source/youtube") {
            return "https://www.youtube.com/live_chat?channel=" + channelID;
        }
        else if (streamSource == "event.stream-source/twitch") {
            return "https://www.twitch.tv/embed/" + channelID + "/chat?darkpopout";
        }
    };

  const renderContent = () => {
    // Loading
    if (channelID == null) {
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
    } else if (channelID == failedToFetch) {
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
                  {streamSource == "event.stream-source/twitch" &&
                  <button
                      className="button twitch__button"
                      type="button"
                      onClick={() => {
                          setChatIsOpen(!chatIsOpen)
                      }}>
                      {chatIsOpen ? 'Close Chat' : 'Open Chat'}
                  </button>
                  }
              </header>
              <div className="aspect-ratio-wide twitch__video">
                <iframe
                    src={streamSourceToStreamUrl()}
                    frameBorder="0"
                    allowFullScreen={true}>
                </iframe>
              </div>
                {/*disable chat for non twitch*/}
                {chatIsOpen && streamSource == "event.stream-source/twitch" &&
                <div className="twitch__chat">
                    <iframe
                        frameBorder="0"
                        scrolling="true"
                        src={streamSourceToChatUrl()}>
                    </iframe>
                </div>
                }
            </div>
            <Vote
                proposition={proposition}
                prevProposition={prevProposition}
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
                        twitchUsername={channelID}
                    />
                    <Suggestion
                        twitchUsername={channelID}/>
                    <Leaderboard
                        twitchUsername={channelID}
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
