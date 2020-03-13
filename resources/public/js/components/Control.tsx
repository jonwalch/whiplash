import React, {useState, useEffect, ChangeEvent, useContext} from "react";
import {LoginContext} from "../contexts/LoginContext";
import {Header} from "./Header";
import {Footer} from "./Footer";
import {baseUrl} from "../config/const";
import {useInterval} from "../common";
import { getEvent, getProp} from "../common/stream"

export interface Event {
    "event/start-time": string;
    "event/running?": boolean;
    "event/channel-id" : string;
    "event/stream-source" : string;
    "event/title": string;
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
    "proposition/result?" : boolean;
}

export const defaultProposition = {
    "proposition/start-time": "",
    "proposition/text": "",
    "proposition/running?": false,
    "proposition/betting-end-time" : "",
    "proposition/result?": false
};

export function Control(props: any) {
    const { loggedInState, setLoggedInState } = useContext(LoginContext);
    const [eventTitle, setEventTitle] = useState("");
    const [twitchUser, setTwitchUser] = useState("");
    const [propText, setPropText] = useState("");
    const [proposition, setProposition] = useState<Proposition>(defaultProposition);
    const [prevProposition, setPrevProposition] = useState<Proposition>(defaultProposition);
    const [eventInfo, setEventInfo] = useState<Event>(defaultEvent);
    const [suggestions, setSuggestions] = useState<any[]>([]);
    const [selectedSuggestions, setSelectedSuggestions] = useState<string[]>([]);

    useEffect(() => {
        getEvent().then((event) => {setEventInfo(event)});
        getProp().then((event) => {
            if (event["current-prop"]){
                setProposition(event["current-prop"]);
            } else {
                setProposition(defaultProposition)
            }
            if (event["previous-prop"]) {
                setPrevProposition(event["previous-prop"]);
            } else {
                setPrevProposition(defaultProposition);
            }
        });
        getSuggestions().then((event) => {setSuggestions(event)});
    }, []);

    useInterval(async () => {
        if (loggedInState.status == "user.status/admin") {
            setEventInfo(await getEvent());
            getProp().then((event) => {
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
            setSuggestions(await getSuggestions());
        }
    }, 3000);

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
                "channel-id": twitchUser,
                "source": "twitch"
            })
        });
        const resp = await response.json();
        if (response.status == 200) {
            alert("Successfully started event")
        } else {
            alert(resp.message)
        }
    };

    const endEvent = async () => {
        const response = await fetch(baseUrl + "admin/event/end", {
            headers: {
                "Content-Type": "application/json",
            },
            method: "POST",
            mode: "same-origin",
            redirect: "error",
        });
        const resp = await response.json();
        if (response.status == 200) {
            alert("Successfully ended current event")
        } else {
            alert(resp.message)
        }
    };

    const createProp = async () => {
        const response = await fetch(baseUrl + "admin/prop", {
            headers: {
                "Content-Type": "application/json",
            },
            method: "POST",
            mode: "same-origin",
            redirect: "error",
            body: JSON.stringify({
                text: propText,
                "end-betting-secs": 33,
            })
        });
        const resp = await response.json();
        if (response.status == 200) {
            alert("Successfully created proposition")
        } else {
            alert(resp.message)
        }
    };

    const endProp = async (result:boolean) => {
        const response = await fetch(baseUrl + "admin/prop/end", {
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
        if (response.status == 200) {
            alert("Successfully ended proposition")
        } else {
            alert(resp.message)
        }
    };

    const getSuggestions = async () => {
        const response = await fetch(baseUrl + "admin/suggestion", {
            headers: {
                "Content-Type": "application/json",
            },
            method: "GET",
            mode: "same-origin",
            redirect: "error",
        });
        const resp = await response.json();
        return resp;
    };

    const dismissSuggestions = async () => {
        const response = await fetch(baseUrl + "admin/suggestion", {
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

    const toggleValid = () => {
        return !(selectedSuggestions.length > 0);
    };

    const suggestionToPropString = (suggestion : any) => {
        if (suggestion) {
            return suggestion["suggestion/text"] + " (suggested by " + suggestion["user/name"] + ")";
        }
    };

    function renderControlMarkup() {
        if (loggedInState.status == "user.status/admin") {
            return (
                <form className="container">
                    <div>Current Event:</div>
                    <div>{JSON.stringify(eventInfo)}</div>
                    {!eventInfo["event/channel-id"] &&
                    <div className="form__group"
                        // TODO: remove inline style
                         style={{marginTop: "30px"}}
                    >
                        <label className="form__label" htmlFor="eventTitle">Event Title</label>
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
                    }
                    {!eventInfo["event/channel-id"] &&
                    <>
                        <div className="form__group">
                            <label className="form__label" htmlFor="twitchUser">Event Twitch User</label>
                            <input
                                className="form__input"
                                value={twitchUser}
                                onChange={(e: ChangeEvent<HTMLInputElement>) => {
                                    setTwitchUser(e.currentTarget.value);
                                }}
                                maxLength={50}
                                minLength={4}
                                name="twitchUser"
                                id="twitchUser"
                            />
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
                    </>
                    }
                    {eventInfo["event/channel-id"] &&
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
                            className="aspect-ratio-wide twitch__video"
                            //TODO: remove inline style
                            style = {{paddingTop: "600px"}}
                        >
                            <iframe
                                // TODO: change this
                                src={"https://player.twitch.tv/?channel=" + eventInfo["event/channel-id"]}
                                frameBorder="0"
                                allowFullScreen={true}>
                            </iframe>
                        </div>
                    </>
                    }
                    <div
                        // TODO: remove inline style
                        style = {{marginTop: "30px"}}
                    >
                        Current Proposition:
                    </div>
                    <div>{JSON.stringify(proposition)}</div>
                    {!proposition["proposition/text"] &&
                    <>
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
                                maxLength={100}
                                minLength= {5}
                                name="propText"
                                id="propText"
                            />
                        </div>
                        <button
                            className="button twitch__button"
                            // TODO: remove inline style
                            style = {{marginRight: "30px"}}
                            type="button"
                            onClick={() => {
                                createProp()
                            }}>
                            Create Proposition
                        </button>
                    </>
                    }
                    {proposition["proposition/text"] &&
                    <>
                        <button
                            className="button twitch__button"
                            style={{marginRight: "30px"}}
                            type="button"
                            onClick={() => {
                                endProp(true)
                            }}>
                            Proposition outcome: True
                        </button>
                        <button
                            className="button twitch__button"
                            type="button"
                            onClick={() => {
                                endProp(false)
                            }}>
                            Proposition outcome: False
                        </button>
                    </>
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
                                dismissSuggestions()
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
