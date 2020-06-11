import React, {useState, useEffect, useContext} from "react";
import { Vote } from "./Vote";
import {EventScore, Leaderboard} from "./Leaderboard";
import { useInterval } from "../common";
import { Bets } from "./Bets";
import {Header} from "./Header";
import {Footer} from "./Footer";
import {getEvent, getProp} from "../common/stream";
import {Suggestion} from "./Suggestion";
import {LoginContext} from "../contexts/LoginContext";
import {getUser} from "../common/getUser";
import moment from "moment";
import { embedBaseUrl } from "../config/const";

export const failedToFetch : string = "failed to fetch";


export function Home(props: any) {
  const { loggedInState, setLoggedInState } = useContext(LoginContext);
  const [channelID, setChannelID] = useState<null | string>(null);
  const [matchName, setMatchName] = useState<string>("");
  const [streamSource, setStreamSource] = useState<string>("");
  const [chatIsOpen, setChatIsOpen] = useState<boolean>(true);
  const [proposition, setProposition] = useState<Object>({});
  const [prevProposition, setPrevProposition] = useState<Object>({});
  const [nextEventTime, setNextEventTime] = useState<string>("");
  const [countdown, setCountdown] = useState<any>(null);

  // child state
  const [eventScoreLeaderboard, setEventScoreLeaderboard] = useState<EventScore[]>([]);

  const getEventWrapper = (event:any) => {
      setChannelID(event["event/channel-id"] || failedToFetch);
      setMatchName(event["event/title"]);
      setStreamSource(event["event/stream-source"]);
      if (event["event/stream-source"] !== "event.stream-source/twitch") {
          setChatIsOpen(false)
      }
      setNextEventTime(event["whiplash/next-event-time"] || "");
      setCountdown(setCountdownWrapper(event["whiplash/next-event-time"] || ""));
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

  // TODO: Redo all of this on the backend, I no longer trust JS/system times
  const setCountdownWrapper = (arg: string) => {
      if (arg == "") {
          return null;
      }

      const now = moment();
      let exp = moment(arg, "YYYY-MM-DDTHH:mm:ssZ");

      if (exp.isBefore(now)) {
          return null;
      }

      const days = exp.diff(now, 'days');
      const hours = exp.subtract(days, 'days').diff(now, 'hours');
      const minutes = exp.subtract(hours, 'hours').diff(now, 'minutes');
      const seconds = exp.subtract(minutes, 'minutes').diff(now, 'seconds');

      return {days: days, hours: hours, minutes: minutes, seconds: seconds}
  };

  // keep user's cash and notifications up to date
  // the other pages don't need to do this regularly, because it doesn't matter if their cash is out of date
  // we also only need to fetch regularly if an event is happening, because that's the only time payouts happens
  useInterval(() => {
    if (channelID != failedToFetch) {
        getUser(setLoggedInState);
    }
  }, 5000);

    useEffect(() => {
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
    // TODO: Digits no longer get skipped, but some digits dont display for an entire second
  }, 500);

  useInterval(() => {
    getEvent().then((event) => {
        getEventWrapper(event)
    });
  }, 10000);

  useInterval(() => {
    if (nextEventTime != "") {
        setCountdown(setCountdownWrapper(nextEventTime))
    }}, 1000);

  const lastWinner = () => {
      const winner: EventScore = eventScoreLeaderboard[0];
      if (winner) {
          return winner.user_name
      }
      return "";
  };

    const displayCountDown = () => {
        return countdown.days + " days, "
            + countdown.hours + " hours, "
            + countdown.minutes + " minutes, "
            + countdown.seconds + " seconds!";
    };

    const streamSourceToStreamUrl = () => {
      if (streamSource == "event.stream-source/cnn-unauth") {
          return "https://fave.api.cnn.io/v1/fav/?video=cvplive/cvpstream0&customer=cnn&edition=domestic&env=prod&isLive=true";
      }
      else if (streamSource == "event.stream-source/youtube") {
          return "https://www.youtube.com/embed/live_stream?channel=" + channelID;
      }
      else if (streamSource == "event.stream-source/twitch") {
         return "https://player.twitch.tv/?channel=" + channelID + "&parent=" + embedBaseUrl;
      }
  };

    const streamSourceToChatUrl = () => {
        // TODO: youtube live chat seems to need the video id, not the channel id
        if (streamSource == "event.stream-source/youtube") {
            return "https://www.youtube.com/live_chat?channel=" + channelID;
        }
        else if (streamSource == "event.stream-source/twitch") {
            return "https://www.twitch.tv/embed/" + channelID + "/chat?darkpopout&parent=" + embedBaseUrl;
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
    // no stream but countdown
    } else if (channelID == failedToFetch && countdown != null) {
        return (
            <div className="twitch is-inactive">
                <div className="container">
                    <div className="twitch__placeholder">
                        <p className="twitch__subtitle">Last Event Winner: {lastWinner()}</p>
                        <h2 className="twitch__subtitle">Next Event in {displayCountDown()}</h2>
                        <p>Join our <a href="https://discord.gg/GsG2G9t">Discord</a> and follow us on <a href="https://twitter.com/whiplashgg">Twitter</a>!</p>
                    </div>
                </div>
            </div>
        );

      // No stream and no countdown to show
    } else if (channelID == failedToFetch) {
      return (
          <div className="twitch is-inactive">
            <div className="container">
              <h2 className="twitch__title">Whiplash is taking a nap</h2>
                <div className="twitch__placeholder">
                    <p className="twitch__subtitle">Last Event Winner: {lastWinner()}</p>
                    <p>Join our <a href="https://discord.gg/GsG2G9t">Discord</a> and follow us on <a href="https://twitter.com/whiplashgg">Twitter</a> to find out when we'll be live next!</p>
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
                        channelID={channelID}
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
