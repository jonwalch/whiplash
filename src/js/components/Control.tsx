import React, {useState, useEffect, ChangeEvent, useContext, } from "react";
import {LoginContext} from "../contexts/LoginContext";
import {Header} from "./Header";
import {Footer} from "./Footer";
import {baseUrl} from "../config/const";
import {useInterval} from "../common";
import {getAllEvents, getEvent, getProp} from "../common/stream"

export interface Event {
    "event/start-time": string;
    "event/running?": boolean;
    "event/channel-id" : string;
    "event/stream-source" : string;
    "event/title": string;
}


export interface NextEvent {
    "whiplash/next-event-time": string;
}

export const defaultEvent = {
    "event/start-time": "",
    "event/running?": false,
    "event/channel-id": "",
    "event/stream-source": "",
    "event/title": "",
};

export interface Proposition {
    "proposition/start-time" : string;
    "proposition/text" : string;
    "proposition/running?" : boolean;
    "proposition/betting-end-time" : string;
    "proposition/result" : string;
}

export const defaultProposition = {
    "proposition/start-time": "",
    "proposition/text": "",
    "proposition/running?": false,
    "proposition/betting-end-time" : "",
    "proposition/result": ""
};

const defaultBetSecs = 30;

export function Control(props: any) {
    const { loggedInState, setLoggedInState } = useContext(LoginContext);
    // const [nextEventTs, setNextEventTs] = useState("");
    const [eventTitle, setEventTitle] = useState("");
    const [inputChannelID, setInputChannelID] = useState("");
    const [propText, setPropText] = useState("");
    const [proposition, setProposition] = useState<Proposition>(defaultProposition);
    const [prevProposition, setPrevProposition] = useState<Proposition>(defaultProposition);
    // any is here to shut up the ts compiler
    const [eventInfo, setEventInfo] = useState<Event | NextEvent | any>(defaultEvent);
    const [suggestions, setSuggestions] = useState<any[]>([]);
    const [selectedSuggestions, setSelectedSuggestions] = useState<string[]>([]);
    const [eventSource, setEventSource] = useState<string>("");
    const [bettingDuration, setBettingDuration] = useState<number>(defaultBetSecs);

    const [selectedChannelID, setSelectedChannelID] = useState<string>("")
    const [runningEvents, setRunningEvents] = useState<Event[]>([])

    useEffect(() => {
        getAllEvents().then((events) => {setRunningEvents(events)})
    }, []);

    const fetchAllEventInfo = async () => {
        if (loggedInState.status === "user.status/admin" || loggedInState.status === "user.status/mod") {
            setRunningEvents(await getAllEvents())
            if (selectedChannelID !== "") {
                setEventInfo(await getEvent(selectedChannelID));
                getProp(selectedChannelID).then((event) => {
                    if (event["current-prop"]){
                        setProposition(event["current-prop"]);
                    } else {
                        setProposition(defaultProposition)
                    }
                    if (event["previous-prop"]) {
                        setPrevProposition(event["previous-prop"]);
                    } else {
                        setPrevProposition(defaultProposition)
                    }
                });
                setSuggestions(await getSuggestions(selectedChannelID));
            }
        }
    }

    useEffect(() => {
        fetchAllEventInfo()
    }, [selectedChannelID])

    useInterval(async () => {
        fetchAllEventInfo()
    }, 1000);

    const createEvent = async () => {
        const response = await fetch(baseUrl + "admin/event", {
            headers: {
                "Content-Type": "application/json",
            },
            method: "POST",
            mode: "same-origin",
            redirect: "error",
            body: JSON.stringify({
                title: eventTitle,
                "channel-id": inputChannelID,
                "source": eventSource
            })
        });
        const resp = await response.json();
        if (response.status === 200) {
            alert("Successfully started event")
        } else {
            alert(resp.message)
        }
        setEventSource("");
    };

    const patchEvent = async () => {
        const response = await fetch(baseUrl + "admin/event/" + selectedChannelID, {
            headers: {
                "Content-Type": "application/json",
            },
            method: "PATCH",
            mode: "same-origin",
            redirect: "error",
            body: JSON.stringify({
                "auto-run": eventInfo["event/auto-run"] === "event.auto-run/csgo" ? "off": "csgo"
            })
        });
        if (response.status === 200) {
            alert("Successfully changed auto run mode")
        } else {
            alert("Failed to change auto run mode")
        }
    };

    const endEvent = async () => {
        const response = await fetch(baseUrl + "admin/event/end/" + selectedChannelID, {
            method: "POST",
            mode: "same-origin",
            redirect: "error",
        });
        const resp = await response.json();
        if (response.status === 200) {
            alert("Successfully ended current event")
            setSelectedChannelID("")
        } else {
            alert(resp.message)
        }
    };

    const createProp = async (channelID : string) => {
        const response = await fetch(baseUrl + "admin/prop/" + channelID, {
            headers: {
                "Content-Type": "application/json",
            },
            method: "POST",
            mode: "same-origin",
            redirect: "error",
            body: JSON.stringify({
                text: propText,
                "end-betting-secs": bettingDuration,
            })
        });
        if (response.status === 200) {
            alert("Successfully created proposition")
        } else {
            alert("ERROR: Couldn't create proposition")
        }
    };

    const endProp = async (result:string) => {
        const response = await fetch(baseUrl + "admin/prop/end/" + selectedChannelID, {
            headers: {
                "Content-Type": "application/json",
            },
            method: "POST",
            mode: "same-origin",
            redirect: "error",
            body: JSON.stringify({
                result: result,
            })
        });
        const resp = await response.json();
        if (response.status === 200) {
            alert("Successfully ended proposition")
        } else {
            alert(resp.message)
        }
    };

    const flipPreviousOutcome = async (channelID : string) => {
        const response = await fetch(baseUrl + "admin/prop/flip-previous/" + channelID, {
            method: "POST",
            mode: "same-origin",
            redirect: "error",
        });
        const resp = await response.json();
        if (response.status === 200) {
            alert("Successfully flipped previous proposition outcome")
        } else {
            alert(resp.message)
        }
    };

    const getSuggestions = async (channelID : string) => {
        const response = await fetch(baseUrl + "admin/suggestion/" +  channelID, {
            method: "GET",
            mode: "same-origin",
            redirect: "error",
        });
        if (response.status === 200) {
            return await response.json()
        } else {
            return []
        }
    };

    const dismissSuggestions = async (channelID : string) => {
        const response = await fetch(baseUrl + "admin/suggestion/" + channelID, {
            headers: {
                "Content-Type": "application/json",
            },
            method: "POST",
            mode: "same-origin",
            body: JSON.stringify({
                suggestions: selectedSuggestions,
            }),
            redirect: "error",
        });
        const resp = await response.json();
        if (response.status == 200) {
            alert("successfully dismissed suggestions")
        }
        else {
            alert("failed to dismiss, error message: " + resp.message)
        }
        setSelectedSuggestions([])
    };

    // const createCountdown = async () => {
    //     const response = await fetch(baseUrl + "admin/event/countdown", {
    //         headers: {
    //             "Content-Type": "application/json",
    //         },
    //         method: "POST",
    //         mode: "same-origin",
    //         body: JSON.stringify({
    //             ts: nextEventTs,
    //         }),
    //         redirect: "error",
    //     });
    //     const resp = await response.json();
    //     if (response.status == 200) {
    //         alert("successfully created countdown")
    //     }
    //     else {
    //         alert("failed to crete countdown, error message: " + resp.message)
    //     }
    // };

    const toggleValid = () => {
        return !(selectedSuggestions.length > 0);
    };

    const suggestionToPropString = (suggestion : any) => {
        if (suggestion) {
            return suggestion["suggestion/text"] + " (suggested by " + suggestion["user/name"] + ")";
        }
    };

    function renderEventInfo() {
        if (selectedChannelID !== ""){
            return (
                <>
                    <div>{JSON.stringify(eventInfo)}</div>
                    <button
                        className="button twitch__button"
                        type="button"
                        onClick={() => {
                            patchEvent()
                        }}>
                        {eventInfo["event/auto-run"] === "event.auto-run/csgo" ? "Turn autorun off" : "Turn CSGO autorun on"}
                    </button>

                </>)
        } else {
            return(<div>No event selected</div>)
        }
    }

    function renderControlMarkup() {
        if (loggedInState.status === "user.status/admin" || loggedInState.status === "user.status/mod" ) {
            return (
                <form className="container">
                    <div className="form__group">
                        {/*<label className="form__label" htmlFor="eventTitle">Next Event Countdown</label>*/}
                        {/*<input*/}
                        {/*    className="form__input"*/}
                        {/*    value={nextEventTs}*/}
                        {/*    onChange={(e: ChangeEvent<HTMLInputElement>) => {*/}
                        {/*        setNextEventTs(e.currentTarget.value);*/}
                        {/*    }}*/}
                        {/*    placeholder="Must be ISO 8601 format in UTC time zone. i.e. 2020-04-01T22:56:01Z"*/}
                        {/*    maxLength={20}*/}
                        {/*    name="nextEventTs"*/}
                        {/*    id="nextEventTs"*/}
                        {/*/>*/}
                        {/*< button*/}
                        {/*    className="button twitch__button"*/}
                        {/*    // TODO: remove inline style*/}
                        {/*    style = {{marginRight: "30px"}}*/}
                        {/*    type="button"*/}
                        {/*    onClick={() => {*/}
                        {/*        createCountdown()*/}
                        {/*    }}>*/}
                        {/*    Create Countdown*/}
                        {/*</button>*/}
                        <label style={{marginTop: "30px"}}
                            // TODO remove inline style
                               className="form__label"
                               htmlFor="eventTitle">Event Title</label>
                        <input
                            className="form__input"
                            value={eventTitle}
                            onChange={(e: ChangeEvent<HTMLInputElement>) => {
                                setEventTitle(e.currentTarget.value);
                            }}
                            maxLength={100}
                            minLength={5}
                            name="eventTitle"
                            id="eventTitle"
                        />
                    </div>
                    <div className="form__group">
                        <label className="form__label" htmlFor="twitchUser">Event Channel ID</label>
                        <input
                            className="form__input"
                            value={inputChannelID}
                            onChange={(e: ChangeEvent<HTMLInputElement>) => {
                                setInputChannelID(e.currentTarget.value);
                            }}
                            maxLength={50}
                            minLength={4}
                            placeholder="Type anything here for cnn or none"
                            name="channelID"
                            id="channelID"
                        />
                    </div>
                    {/*TODO: remove inline style*/}
                    <div style={{display: "flex",
                        flexDirection: "column"
                    }}
                    >
                        <div>
                            <input
                                type='radio'
                                checked={eventSource === "twitch"}
                                name="twitchRadioButton"
                                key="twitchRadioButton"
                                onChange={() => {setEventSource("twitch")}}>
                            </input>
                            <label htmlFor="twitchRadioButton">Twitch</label>
                            {/*<input*/}
                            {/*    type='radio'*/}
                            {/*    checked={eventSource === "youtube"}*/}
                            {/*    name="youTubeRadioButton"*/}
                            {/*    key="youTubeRadioButton"*/}
                            {/*    onChange={() => {setEventSource("youtube")}}>*/}
                            {/*</input>*/}
                            {/*<label htmlFor="youTubeRadioButton">YouTube Live</label>*/}
                            {/*<input*/}
                            {/*    type='radio'*/}
                            {/*    checked={eventSource === "cnn-unauth"}*/}
                            {/*    name="cnnUnAuthRadioButton"*/}
                            {/*    key="cnnUnAuthRadioButton"*/}
                            {/*    onChange={() => {setEventSource("cnn-unauth")}}>*/}
                            {/*</input>*/}
                            {/*<label htmlFor="cnnUnAuthRadioButton">CNN Unauth</label>*/}
                            <input
                                type='radio'
                                checked={eventSource === "none"}
                                name="noneRadioButton"
                                key="noneRadioButton"
                                onChange={() => {setEventSource("none")}}>
                            </input>
                            <label htmlFor="noneRadioButton">None</label>
                        </div>
                        < button
                            className="button twitch__button"
                            // TODO: remove inline style
                            style = {{marginRight: "30px"}}
                            type="button"
                            onClick={() => {
                                createEvent()
                            }}>
                            Create Event
                        </button>
                    </div>
                    <div style={{marginTop: "0.5rem"}}>Current Events:</div>
                    <div style={{display: "flex", flexWrap: "wrap"}}>
                        {runningEvents.length > 0 && runningEvents.map((event: Event) => {
                            return (
                                <button
                                    onClick={(e:any) => {
                                        e.preventDefault()
                                        setSelectedChannelID(e.target.value)
                                    }}
                                    type="button"
                                    className="button twitch__button"
                                    style={{marginRight: "0.5rem"}}
                                    value={event["event/channel-id"]}
                                    key={event["event/channel-id"]}>
                                    {event["event/channel-id"]}
                                </button>
                            );
                        })}
                    </div>
                    <div style={{marginTop: "1rem"}}>Selected Event:</div>
                    {renderEventInfo()}
                    {selectedChannelID &&
                    <>
                        <button
                            className="button twitch__button"
                            type="button"
                            onClick={() => {
                            endEvent()
                        }}>
                            End Event
                        </button>
                    <div
                        // TODO: remove inline style
                        style = {{marginTop: "30px"}}
                    >
                        Current Proposition:
                    </div>
                    <div>{proposition["proposition/text"] || "N/A"}</div>
                    {proposition["proposition/text"] &&
                    <>
                        <button
                            className="button twitch__button"
                            style={{marginRight: "30px"}}
                            type="button"
                            onClick={() => {
                                endProp("true")
                            }}>
                            Proposition outcome: True
                        </button>
                        <button
                            className="button twitch__button"
                            type="button"
                            onClick={() => {
                                endProp("false")
                            }}>
                            Proposition outcome: False
                        </button>
                        <button
                            className="button twitch__button"
                            style={{background: "red"}}
                            type="button"
                            onClick={() => {
                                endProp("cancel")
                            }}>
                            CANCEL PROPOSITION
                        </button>
                    </>
                    }
                    <div className="form__group"
                        // TODO: remove inline style
                         style = {{marginTop: "30px"}}
                    >
                        <label className="form__label" htmlFor="propText">Proposition Text</label>
                        <input
                            className="form__input"
                            value={propText}
                            onChange={(e: ChangeEvent<HTMLInputElement>) => {
                                setPropText(e.currentTarget.value);
                            }}
                            maxLength={150}
                            minLength= {5}
                            name="propText"
                            id="propText"
                        />
                    </div>
                    <div className="form__group"
                        // TODO: remove inline style
                         style = {{marginTop: "30px"}}
                    >
                        <label className="form__label" htmlFor="betSecs">Bet Duration (add 1 to what you want it to display)</label>
                        <input
                            className="form__input"
                            value={bettingDuration}
                            onChange={(e: ChangeEvent<HTMLInputElement>) => {
                                setBettingDuration(parseInt(e.currentTarget.value));
                            }}
                            maxLength={3}
                            minLength= {1}
                            type="number"
                            name="betSecs"
                            id="betSecs"
                        />
                    </div>
                    {!proposition["proposition/text"] &&
                    <button
                        className="button twitch__button"
                        // TODO: remove inline style
                        style = {{marginRight: "30px"}}
                        type="button"
                        onClick={() => {
                            if (bettingDuration >= 10) {
                                createProp(selectedChannelID)
                            } else {
                                alert("Enter a valid value for betting duration")
                            }
                        }}>
                        Create Proposition
                    </button>
                    }
                    <div
                        // TODO: remove inline style
                        style = {{marginTop: "30px"}}>
                        Suggestions:</div>
                    <div>
                        <select
                            style={{backgroundColor: "black",
                                height: "300px"}}
                            multiple={true}
                            value={selectedSuggestions}
                            name="suggestions"
                            onChange={(e : any) => {
                                setSelectedSuggestions(
                                    Array.from(e.target.options)
                                        .filter((o: any) => o.selected)
                                        .map((o: any) => o.value))
                            }}
                        >
                            {suggestions.length > 0 && suggestions.map((suggestion: any) => {
                                return (
                                    <option
                                        value={suggestion["suggestion/uuid"]}
                                        key={suggestion["suggestion/uuid"]}>
                                        {suggestionToPropString(suggestion)}
                                    </option>
                                );
                            })}
                        </select>
                    </div>
                    {selectedSuggestions.length > 0 &&
                    <>
                        <button
                            // TODO: gray out this button when clicking it is invalid
                            className="button twitch__button"
                            // TODO: remove inline style
                            style = {{marginRight: "30px"}}
                            type="button"
                            disabled={toggleValid()}
                            onClick={() => {
                                dismissSuggestions(selectedChannelID)
                            }}
                        >
                            Dismiss Suggestions
                        </button>
                        <button
                            // TODO: gray out this button when clicking it is invalid
                            className="button twitch__button"
                            type="button"
                            onClick={() => {
                                const targetSuggestion = suggestions
                                    .filter((suggestion : any) =>
                                        suggestion["suggestion/uuid"] == selectedSuggestions[0])[0];
                                setPropText(suggestionToPropString(targetSuggestion) || "")
                            }}
                        >
                            Move to Prop
                        </button>
                    </>
                    }
                    <div>Previous Proposition:</div>
                    <div>{JSON.stringify(prevProposition)}</div>
                    <button
                        className="button twitch__button"
                        type="button"
                        onClick={() => {
                            flipPreviousOutcome(selectedChannelID)
                        }}>
                        Flip Previous Prop Outcome
                    </button>
                    </>
                    }
                </form>
            );
        }
        return (
            <>
                <div>404</div>
            </>
        );
    }

    return (
        <>
            <Header/>
            <main id="content" role="main" className="article">
                {renderControlMarkup()}
            </main>
            <Footer/>
        </>
    );
};
